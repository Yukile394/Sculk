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
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("sculk");
   private static final Path FILE = DIR.resolve("config.json");

   private ConfigManager() {
   }

   public static Path directory() {
      return DIR;
   }

   public static void save(List<Module> modules) {
      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", "1.0.0-beta");
         JsonObject moduleObject = new JsonObject();

         for (Module module : modules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("key", module.getKey());
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
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, GSON.toJson(root));
      } catch (RuntimeException | IOException var9) {
      }
   }

   public static void load(List<Module> modules) {
      if (Files.isRegularFile(FILE)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (!root.has("modules")) {
               return;
            }

            JsonObject moduleObject = root.getAsJsonObject("modules");

            for (Module module : modules) {
               if (moduleObject.has(module.getName())) {
                  JsonObject entry = moduleObject.getAsJsonObject(module.getName());
                  if (entry.has("key")) {
                     module.key(entry.get("key").getAsInt());
                  }

                  if (entry.has("settings")) {
                     JsonObject settings = entry.getAsJsonObject("settings");

                     for (Setting<?> setting : module.settings()) {
                        if (settings.has(setting.getName())) {
                           setting.deserialize(settings.get(setting.getName()).getAsString());
                        }
                     }
                  }

                  if (entry.has("enabled")) {
                     module.setEnabledSilently(entry.get("enabled").getAsBoolean());
                  }
               }
            }
         } catch (RuntimeException | IOException var9) {
         }
      }
   }
}
