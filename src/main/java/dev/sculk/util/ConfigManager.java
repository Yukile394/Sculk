package dev.sculk.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ButtonSetting;
import dev.sculk.module.setting.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("sculk");
   private static final Path FILE = DIR.resolve("config.json");
   private static final Path CONFIGS = DIR.resolve("configs");

   private ConfigManager() {
   }

   public static Path directory() {
      return DIR;
   }

   public static void save(List<Module> modules) {
      write(FILE, modules);
   }

   public static void load(List<Module> modules) {
      read(FILE, modules, true);
   }

   /** Names of the saved configs in config/sculk/configs, without extension. */
   public static List<String> list() {
      List<String> names = new ArrayList<>();
      if (Files.isDirectory(CONFIGS)) {
         try (Stream<Path> files = Files.list(CONFIGS)) {
            files.map(path -> path.getFileName().toString())
               .filter(name -> name.endsWith(".json"))
               .map(name -> name.substring(0, name.length() - 5))
               .sorted(String.CASE_INSENSITIVE_ORDER)
               .forEach(names::add);
         } catch (IOException var6) {
         }
      }

      return names;
   }

   public static boolean saveNamed(String name, List<Module> modules) {
      String clean = clean(name);
      if (clean.isEmpty()) {
         return false;
      }

      write(CONFIGS.resolve(clean + ".json"), modules);
      return true;
   }

   public static boolean loadNamed(String name, List<Module> modules) {
      Path path = CONFIGS.resolve(clean(name) + ".json");
      if (!Files.isRegularFile(path)) {
         return false;
      }

      read(path, modules, false);
      return true;
   }

   public static String clean(String name) {
      return name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
   }

   private static void write(Path file, List<Module> modules) {
      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", "1.1.0");
         JsonObject moduleObject = new JsonObject();

         for (Module module : modules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isPersistent() && module.isEnabled());
            entry.addProperty("key", module.getKey());
            entry.addProperty("keySet", true);
            entry.addProperty("hold", module.isHoldBind());
            JsonObject settings = new JsonObject();

            for (Setting<?> setting : module.settings()) {
               if (!(setting instanceof ButtonSetting)) {
                  settings.addProperty(setting.getName(), setting.serialize());
               }
            }

            entry.add("settings", settings);
            moduleObject.add(module.getName(), entry);
         }

         root.add("modules", moduleObject);
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(root));
      } catch (RuntimeException | IOException var9) {
      }
   }

   private static void read(Path file, List<Module> modules, boolean silent) {
      if (Files.isRegularFile(file)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("modules")) {
               return;
            }

            JsonObject moduleObject = root.getAsJsonObject("modules");

            for (Module module : modules) {
               if (moduleObject.has(module.getName())) {
                  JsonObject entry = moduleObject.getAsJsonObject(module.getName());
                  if (entry.has("key")) {
                     int saved = entry.get("key").getAsInt();
                     boolean explicit = entry.has("keySet") && entry.get("keySet").getAsBoolean();
                     // old configs saved -1 for everything, which must not wipe the default binds
                     if (explicit || saved != -1) {
                        module.key(saved);
                     }
                  }

                  if (entry.has("hold")) {
                     module.setHoldBind(entry.get("hold").getAsBoolean());
                  }

                  if (entry.has("settings")) {
                     JsonObject settings = entry.getAsJsonObject("settings");

                     for (Setting<?> setting : module.settings()) {
                        if (settings.has(setting.getName())) {
                           setting.deserialize(settings.get(setting.getName()).getAsString());
                        }
                     }
                  }

                  if (entry.has("enabled") && module.isPersistent()) {
                     boolean state = entry.get("enabled").getAsBoolean();
                     if (silent) {
                        module.setEnabledSilently(state);
                     } else {
                        module.setEnabled(state);
                     }
                  }
               }
            }
         } catch (RuntimeException | IOException var9) {
         }
      }
   }
}
