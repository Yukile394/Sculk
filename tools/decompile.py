#!/usr/bin/env python3
"""
Fabric mod jar -> Gradle projesi kaynak çıkarıcı.

Verilen jar içinden:
  1. --project-prefix altındaki sınıfları Vineflower ile decompile eder,
  2. intermediary isimleri (class_/method_/field_) yarn isimlerine çevirir,
  3. varsa accesswidener'ı intermediary -> named'e dönüştürür,
  4. resource'ları (fabric.mod.json, mixin json, icon, assets, data) çıkarır,
  5. proje dışı gömülü kütüphaneler varsa libs/bundled-libs.jar olarak ayırır,
  6. DECOMPILE_REPORT.md yazar.

Kullanım:
  python3 tools/decompile.py --jar original/Sculk.jar --project-prefix dev/sculk/ \
      --yarn 1.20.1+build.10 [--root .] [--force]
"""
import argparse
import json
import logging
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

LOG = logging.getLogger("decompile")

VINEFLOWER_VERSION = "1.10.1"
MAVEN = "https://maven.fabricmc.net"
LIBS_JAR_NAME = "bundled-libs.jar"
RESOURCE_DIR_PREFIXES = ("assets/", "data/")
SKIP_FILES = {"META-INF/MANIFEST.MF", "META-INF/INDEX.LIST"}
LICENSE_RE = re.compile(r"(?i)^(license|licence|notice|copying)[^/]*$")

DOT_CLASS = re.compile(r"net\.minecraft\.class_\d+(?:[.$]class_\d+)*")
SLASH_CLASS = re.compile(r"net/minecraft/class_\d+(?:\$class_\d+)*")
SIMPLE_CLASS = re.compile(r"\bclass_\d+\b")
MEMBER = re.compile(r"\b(?:method|field|comp)_\d+\b")
IMPORT_LINE = re.compile(
    r"^import (net\.minecraft\.class_\d+(?:[.$]class_\d+)*);[ \t]*$", re.M
)
FAIL_MARKERS = ("Couldn't be decompiled", "$VF: Unable to", "Method decompilation error")


INDY_CONCAT = re.compile(
    r'StringConcatFactory\.makeConcatWithConstants<((?:"(?:[^"\\]|\\.)*"\s*,?\s*)+)>\('
)
STR_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
RECIPE_MARKERS = re.compile(r"(\\u0001|\\u0002|\x01|\x02)")


def _split_call_args(text: str, start: int):
    """start: '(' sonrası indeks. (argümanlar, kapanış ')' indeksi) döner, bulamazsa None."""
    depth = 0
    i = start
    cur = start
    args = []
    n = len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            quote = c
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    i += 1
                i += 1
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                args.append(text[cur:i].strip())
                return args, i
            depth -= 1
        elif c == "," and depth == 0:
            args.append(text[cur:i].strip())
            cur = i + 1
        i += 1
    return None


def fix_indy_concat(text: str) -> str:
    """Vineflower'ın çıkaramadığı invokedynamic string birleştirmeyi düz Java'ya çevirir."""
    out = []
    pos = 0
    while True:
        m = INDY_CONCAT.search(text, pos)
        if not m:
            out.append(text[pos:])
            break
        parsed = _split_call_args(text, m.end())
        literals = STR_LITERAL.findall(m.group(1))
        if parsed is None or len(literals) < 2:
            out.append(text[pos:m.end()])
            pos = m.end()
            continue
        args, close = parsed
        if args == [""]:
            args = []
        recipe = literals[1]
        constants = literals[2:]
        pieces = []
        a_i = c_i = 0
        ok = True
        for frag in RECIPE_MARKERS.split(recipe):
            if frag in ("\\u0001", "\x01"):
                if a_i >= len(args):
                    ok = False
                    break
                pieces.append("(" + args[a_i] + ")")
                a_i += 1
            elif frag in ("\\u0002", "\x02"):
                if c_i >= len(constants):
                    ok = False
                    break
                pieces.append('"' + constants[c_i] + '"')
                c_i += 1
            elif frag:
                pieces.append('"' + frag + '"')
        if not ok or a_i != len(args):
            out.append(text[pos:m.end()])
            pos = m.end()
            continue
        out.append(text[pos:m.start()])
        out.append('("" + ' + " + ".join(pieces) + ")" if pieces else '""')
        pos = close + 1
    return "".join(out)


DECL = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_]\w*)")
PLAIN_IMPORT = re.compile(r"^import ([\w.]+)\.([A-Za-z_]\w*);[ \t]*$", re.M)
DUCK_CAST = re.compile(r"\(\((I[A-Z]\w*)\)([A-Za-z_][\w.]*)\)")
GETVALUE_COND = re.compile(r"((?:[A-Za-z_]\w*(?:\([^()]*\))?\.)+getValue\(\)) \?")
BOOL_ASSIGN = re.compile(r"\b([A-Za-z_]\w*) = \(boolean\)(\d+)")
INT_USE = r"(?:\b{n}\s*(?:\+\+|--|\+=|-=)|(?:\+\+|--){n}\b)"


def fix_bool_int(text: str) -> str:
    """Vineflower bazen int sayacı boolean sanıyor: (boolean)0 + x++ kalıbını düzeltir."""
    names = {m.group(1) for m in BOOL_ASSIGN.finditer(text)}
    for name in names:
        if not re.search(INT_USE.format(n=re.escape(name)), text):
            continue
        n = re.escape(name)
        text = re.sub(rf"\bboolean {n}\b", f"int {name}", text)
        text = re.sub(rf"\b{n} = \(boolean\)(\d+)", rf"{name} = \1", text)
        text = re.sub(rf"(\bint {n} = )false\b", r"\g<1>0", text)
        text = re.sub(rf"(\bint {n} = )true\b", r"\g<1>1", text)
        text = re.sub(rf"(\b{n} = )false;", r"\g<1>0;", text)
        text = re.sub(rf"(\b{n} = )true;", r"\g<1>1;", text)
    return text


def fix_duck_casts(text: str) -> str:
    """((IFoo)x) -> ((IFoo)(Object)x): mixin duck interface cast'leri javac'ta Object üzerinden geçmeli."""
    return DUCK_CAST.sub(lambda m: f"(({m.group(1)})(Object){m.group(2)})", text)


def fix_getvalue_cond(text: str) -> str:
    """setting.getValue() ? a : b  ->  ((Boolean) setting.getValue()) ? a : b"""
    return GETVALUE_COND.sub(lambda m: f"((Boolean) {m.group(1)}) ?", text)


def download(url: str, dest: Path) -> None:
    LOG.info("indiriliyor: %s", url)
    req = urllib.request.Request(url, headers={"User-Agent": "mod-bootstrap"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as fh:
        shutil.copyfileobj(resp, fh)


def yarn_url(version: str) -> str:
    return f"{MAVEN}/net/fabricmc/yarn/{version}/yarn-{version}-mergedv2.jar"


def resolve_yarn(requested: str, dest: Path) -> str:
    try:
        download(yarn_url(requested), dest)
        return requested
    except Exception as exc:  # noqa: BLE001
        LOG.warning("yarn %s indirilemedi (%s), en günceli aranıyor", requested, exc)
    mc = requested.split("+")[0]
    req = urllib.request.Request(
        f"{MAVEN}/net/fabricmc/yarn/maven-metadata.xml",
        headers={"User-Agent": "mod-bootstrap"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        meta = resp.read().decode("utf-8")
    builds = sorted(
        int(b) for b in re.findall(rf"<version>{re.escape(mc)}\+build\.(\d+)</version>", meta)
    )
    if not builds:
        raise SystemExit(f"HATA: {mc} için yarn sürümü bulunamadı")
    version = f"{mc}+build.{builds[-1]}"
    download(yarn_url(version), dest)
    return version


class Renamer:
    def __init__(self, tiny_text: str) -> None:
        lines = tiny_text.splitlines()
        hdr = lines[0].split("\t")
        if hdr[:2] != ["tiny", "2"]:
            raise SystemExit(f"HATA: beklenmeyen mapping başlığı: {hdr}")
        namespaces = hdr[3:]
        if "intermediary" not in namespaces or "named" not in namespaces:
            raise SystemExit(f"HATA: intermediary/named yok: {hdr}")
        i_idx = namespaces.index("intermediary")
        n_idx = namespaces.index("named")
        self.classes = {}
        self.members = {}
        for line in lines[1:]:
            if line.startswith("c\t"):
                p = line.split("\t")
                if len(p) > 1 + max(i_idx, n_idx):
                    inter, named = p[1 + i_idx], p[1 + n_idx]
                    if inter and named:
                        self.classes[inter] = named
            elif line.startswith("\t") and not line.startswith("\t\t"):
                p = line[1:].split("\t")
                if p[0] in ("m", "f") and len(p) > 2 + max(i_idx, n_idx):
                    inter, named = p[2 + i_idx], p[2 + n_idx]
                    if inter and named and inter != named:
                        self.members[inter] = named
        self.simple = {}
        self.fqn = {}
        for inter, named in self.classes.items():
            tok = re.split(r"[/$]", inter)[-1]
            if not tok.startswith("class_"):
                continue
            self.simple[tok] = re.split(r"[/$]", named)[-1]
            self.fqn[tok] = named.replace("/", ".").replace("$", ".")
        LOG.info("mapping: %d sınıf, %d üye", len(self.classes), len(self.members))

    def _dot(self, raw: str) -> str:
        parts = raw.split(".")
        key = "net/minecraft/" + "$".join(parts[2:])
        named = self.classes.get(key)
        return named.replace("/", ".").replace("$", ".") if named else raw

    def apply(self, text: str, package_names=frozenset()) -> str:
        declared = set(package_names)
        declared.update(DECL.findall(text))
        for m in PLAIN_IMPORT.finditer(text):
            if not m.group(1).startswith("net.minecraft") or "class_" not in m.group(0):
                declared.add(m.group(2))
        kept = {}
        conflicts = {}
        for m in IMPORT_LINE.finditer(text):
            raw = m.group(1)
            named_fqn = self._dot(raw)
            if named_fqn == raw:
                continue
            simple_named = named_fqn.rsplit(".", 1)[-1]
            tok = re.split(r"[.$]", raw)[-1]
            prev = kept.get(simple_named)
            if simple_named in declared:
                conflicts[tok] = named_fqn
            elif prev is None:
                kept[simple_named] = named_fqn
            elif prev != named_fqn:
                conflicts[tok] = named_fqn
        if conflicts:
            text = IMPORT_LINE.sub(
                lambda m: "" if re.split(r"[.$]", m.group(1))[-1] in conflicts else m.group(0),
                text,
            )
        text = DOT_CLASS.sub(lambda m: self._dot(m.group(0)), text)
        text = SLASH_CLASS.sub(lambda m: self.classes.get(m.group(0), m.group(0)), text)
        text = SIMPLE_CLASS.sub(
            lambda m: conflicts.get(m.group(0)) or self.simple.get(m.group(0), m.group(0)),
            text,
        )
        text = MEMBER.sub(lambda m: self.members.get(m.group(0), m.group(0)), text)
        return text

    def convert_access_widener(self, text: str) -> str:
        out = []
        for i, line in enumerate(text.splitlines()):
            if i == 0:
                out.append(re.sub(r"\bintermediary\b", "named", line))
                continue
            line = SLASH_CLASS.sub(lambda m: self.classes.get(m.group(0), m.group(0)), line)
            line = MEMBER.sub(lambda m: self.members.get(m.group(0), m.group(0)), line)
            out.append(line)
        return "\n".join(out) + "\n"


def _as_name_list(value):
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        names = []
        for v in value.values():
            names.extend(_as_name_list(v))
        return names
    if isinstance(value, list):
        names = []
        for v in value:
            names.extend(_as_name_list(v))
        return names
    return []


def read_mod_info(zin: zipfile.ZipFile):
    """(resource_adları, refmap_adları, accesswidener_adı) döner."""
    meta = json.loads(zin.read("fabric.mod.json").decode("utf-8"))
    resources = {"fabric.mod.json"}
    refmaps = set()
    for entry in meta.get("mixins", []):
        name = entry if isinstance(entry, str) else entry.get("config")
        if not name:
            continue
        resources.add(name)
        try:
            cfg = json.loads(zin.read(name).decode("utf-8"))
            if cfg.get("refmap"):
                refmaps.add(cfg["refmap"])
        except (KeyError, ValueError):
            LOG.warning("mixin config okunamadı: %s", name)
    aw = meta.get("accessWidener")
    resources.update(_as_name_list(meta.get("icon")))
    return resources, refmaps, aw


def split_jar(jar: Path, work: Path, root: Path, prefixes):
    classes_dir = work / "classes"
    classes_dir.mkdir(parents=True, exist_ok=True)
    libs_dir = root / "libs"
    res_dir = root / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)
    prefix_tuple = tuple(prefixes)

    n_classes = n_res = 0
    lib_entries = []
    with zipfile.ZipFile(jar) as zin:
        res_names, refmaps, aw_name = read_mod_info(zin)
        for info in zin.infolist():
            name = info.filename
            if info.is_dir():
                continue
            if name.startswith(prefix_tuple) and name.endswith(".class"):
                zin.extract(info, classes_dir)
                n_classes += 1
            elif name == aw_name:
                continue
            elif name in SKIP_FILES or name in refmaps or name.endswith("-refmap.json"):
                continue
            elif name.startswith(prefix_tuple) or name in res_names or name.startswith(RESOURCE_DIR_PREFIXES):
                target = res_dir / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(zin.read(info))
                n_res += 1
            elif LICENSE_RE.match(name):
                (root / name).write_bytes(zin.read(info))
            else:
                lib_entries.append(info)

        libs_jar = libs_dir / LIBS_JAR_NAME
        if lib_entries:
            libs_dir.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(libs_jar, "w", zipfile.ZIP_DEFLATED) as zlib:
                for info in lib_entries:
                    zlib.writestr(info.filename, zin.read(info))
        elif libs_jar.exists():
            libs_jar.unlink()
    LOG.info("sınıf: %d, resource: %d, lib girdisi: %d", n_classes, n_res, len(lib_entries))
    if n_classes == 0:
        raise SystemExit("HATA: --project-prefix altında sınıf yok: " + ", ".join(prefixes))
    return classes_dir, aw_name


def run_vineflower(vf: Path, classes_dir: Path, original: Path, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        "java", "-Xmx4g", "-jar", str(vf),
        "-dgs=1", "-asc=1", "-rsy=1", "-log=WARN",
        f"-e={original}",
        str(classes_dir), str(out_dir),
    ]
    LOG.info("Vineflower çalışıyor")
    LOG.debug("komut: %s", " ".join(cmd))
    proc = subprocess.run(cmd, check=False)
    if proc.returncode != 0:
        LOG.warning("Vineflower çıkış kodu %d (kısmi çıktı olabilir)", proc.returncode)


def update_gradle_yarn(root: Path, version: str) -> None:
    props = root / "gradle.properties"
    if not props.exists():
        return
    text = props.read_text(encoding="utf-8")
    new = re.sub(r"(?m)^yarn_mappings=.*$", f"yarn_mappings={version}", text)
    if new != text:
        props.write_text(new, encoding="utf-8")
        LOG.info("gradle.properties yarn_mappings=%s", version)


def main() -> int:
    ap = argparse.ArgumentParser(description="Fabric mod jar -> Gradle projesi")
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--root", default=".", type=Path)
    ap.add_argument("--project-prefix", action="append", required=True,
                    help="decompile edilecek paket yolu, örn. dev/sculk/ (birden fazla verilebilir)")
    ap.add_argument("--yarn", required=True, help="örn. 1.20.1+build.10")
    ap.add_argument("--vineflower", default=VINEFLOWER_VERSION)
    ap.add_argument("--force", action="store_true", help="mevcut src/main/java üzerine yaz")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="[%(levelname)s] %(message)s",
    )

    jar = args.jar.resolve()
    root = args.root.resolve()
    if not jar.is_file():
        LOG.error("jar bulunamadı: %s", jar)
        return 2
    if shutil.which("java") is None:
        LOG.error("java PATH'te yok")
        return 2

    java_dir = root / "src" / "main" / "java"
    if java_dir.exists() and any(java_dir.rglob("*.java")) and not args.force:
        LOG.error("%s dolu; --force ile tekrar çalıştır", java_dir)
        return 3

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        yarn_jar = work / "yarn.jar"
        yarn_version = resolve_yarn(args.yarn, yarn_jar)
        update_gradle_yarn(root, yarn_version)
        with zipfile.ZipFile(yarn_jar) as z:
            renamer = Renamer(z.read("mappings/mappings.tiny").decode("utf-8"))

        vf = work / "vineflower.jar"
        download(
            f"https://github.com/Vineflower/vineflower/releases/download/"
            f"{args.vineflower}/vineflower-{args.vineflower}.jar",
            vf,
        )

        prefixes = [p if p.endswith("/") else p + "/" for p in args.project_prefix]
        classes_dir, aw_name = split_jar(jar, work, root, prefixes)

        if aw_name:
            with zipfile.ZipFile(jar) as zin:
                aw_raw = zin.read(aw_name).decode("utf-8")
            aw_path = root / "src" / "main" / "resources" / aw_name
            aw_path.parent.mkdir(parents=True, exist_ok=True)
            aw_path.write_text(renamer.convert_access_widener(aw_raw), encoding="utf-8")
            LOG.info("accesswidener yazıldı: %s", aw_path)

        raw_out = work / "decompiled"
        run_vineflower(vf, classes_dir, jar, raw_out)

        if java_dir.exists():
            shutil.rmtree(java_dir)
        java_dir.mkdir(parents=True)

        sources = sorted(raw_out.rglob("*.java"))
        package_names = {}
        for src in sources:
            pkg = ".".join(src.relative_to(raw_out).parts[:-1])
            package_names.setdefault(pkg, set()).add(src.stem)

        failed = []
        total = 0
        for src in sources:
            rel = src.relative_to(raw_out)
            pkg = ".".join(rel.parts[:-1])
            text = src.read_text(encoding="utf-8", errors="replace")
            if any(marker in text for marker in FAIL_MARKERS):
                failed.append(str(rel))
            text = fix_indy_concat(text)
            text = renamer.apply(text, package_names.get(pkg, set()))
            text = fix_bool_int(text)
            text = fix_duck_casts(text)
            text = fix_getvalue_cond(text)
            dest = java_dir / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(text, encoding="utf-8")
            total += 1

    report = [
        "# Decompile raporu",
        "",
        f"- Kaynak jar: `{jar.name}`",
        f"- Yarn: `{yarn_version}`",
        f"- Vineflower: `{args.vineflower}`",
        f"- Yazılan .java dosyası: {total}",
        f"- Sorunlu dosya: {len(failed)}",
        "",
        "Sorunlu dosyalar elle düzeltilmeli (decompiler tam çıkaramadı):",
        "",
    ]
    report += [f"- `{f}`" for f in failed] or ["- yok"]
    (root / "DECOMPILE_REPORT.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    LOG.info("bitti: %d dosya, %d sorunlu", total, len(failed))
    return 0 if total > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
