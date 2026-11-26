# sculk (decompile projesi)

Orijinal: `sculk` client (Fabric 1.21.1), yazar: **olivia**.
`fabric.mod.json` lisansı: **ARR (All Rights Reserved)**.

> Bu lisans, kaynağın başkaları tarafından kopyalanıp yayınlanmasına izin vermez.
> Repoyu **public** yapmadan önce yazarın (olivia) açık izni gerekir.
> İzin yoksa repoyu **private** tut ve bir açık kaynak lisansı (GPL/MIT) ekleme.

## Hedef

| | |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.116.10+1.21.1 |
| Yarn | 1.21.1+build.3 |
| Loom / Gradle | 1.7-SNAPSHOT / 8.8 |
| Java | 21 |
| Main | `dev.sculk.SculkClient` |

## Kullanım (sırayla)

1. Dosyaları repoya yükle (`original/Sculk.jar` dahil).
2. Actions → **Bootstrap (decompile)** → Run workflow. `src/main/java` dolar.
3. Actions → **Build** → Run workflow. Jar: çalışmanın Artifacts bölümünde `sculk`.
4. Build kırmızıysa "Hata özeti" adımının çıktısına göre dosyaları düzelt.

Bootstrap'i `force` ile tekrar çalıştırmak `src/main/java` içindeki elle düzenlemeleri siler.

## Yerel build

```
./gradlew build
```

## Değişiklikler (ZeltrixWare portu)

- ClickGUI ZeltrixWare tarzı: sürüklenebilir paneller, arama (Ctrl+F), Friends ve Configs panelleri, GUI renk seçici, açıklama baloncuğu. Orta tık = bind (Toggle/Hold), sağ tık = ayar aç.
- Sesler: enable / disable / boolean / scroll / swipein / swipeout / keypress (Client > Sounds).
- Aim Assist varsayılan tuş: M.
- Auto Firework: slot kilidi, 3.2 blok içinde fişek slotu <-> silah slotu döngüsü, dahili Trigger (EclipseCrit / PhantomCrit), Aim Assist uyumu. Trigger açıkken Trigger Bot kapanır.
- Criticals modülü (Zeltrix modları), Target ESP (Yukile Helix / Orbit + Halo, Pulse, Cage).
- GUI fontu: Client > GUI Colors > SF Font.
- AttackAura kaldırıldı. Yerine Tera Client portu: Aura (HvH / Smooth rotation, GCD quantizer, tremor, rastgele saldırı gecikmesi, Smart/Only crit, sprint drop, shield breaker) ve AutoTotem (Default / Alternative / Matrix / MatrixPick / NewVersion, Safety ayarları). Hepsi Combat kategorisinde. ElytraTarget kaldırıldı.

- Auto Firework: **Silent** hotbar slotunu hiç değiştirmez. Fişek offhand'den atılır; offhand boşsa fişek oraya alınır, doluysa (kalkan / totem) fişek 1 tick için offhand ile takas edilip geri alınır. Sadece envanter tıklaması + offhand kullanımı gider, HELD_ITEM_CHANGE gitmez. Kullanım sonrası el sallanır.
- ClickGUI altında kalıcı "Made By Hikmet" yazısı (ortalı).
- ClickGUI yeniden tasarlandı: gece gradyanlı arka plan, düşen kar (`render/Snow.java`, derinlik + fare rüzgârı), yuvarlatılmış gölgeli kartlar, arama hapı, modern satır / toggle / slider görünümü.
- Tüm yazılar artık Minecraft'ın kendi fontu yerine gömülü SF Medium (normal) ve SF Bold (başlık) ile çizilir (`render/Fonts.java`; HUD dahil `Render2D.text` de aynı fontu kullanır). "SF Font" ayarı kaldırıldı.
- Kalın yazı: sf_bold.ttf oyunda kutu (eksik glyph) gösterdiği için kalın yazılar artık SF Medium + vanilla bold stiliyle çizilir.
- ElytraBooster (Combat): Aura hedefi ve Auto Firework ile birlikte çalışır. Mesafeye göre Approach / Strike / Lead evreleri, roketi bir önceki bitmeden yeniden atma, yanlış yöne roket atmama, Overtake ile hedefin önüne geçme. Modlar: Physics (sadece roket), Emulate (ek vanilla roket adımı), Raw (düz itki). Sunucu pozisyon düzeltmesinde (setback) Emulate/Raw hız sınırını kendi öğrenir ve geri çekilir. Debug ayarı saniyede bir durum satırı verir.
