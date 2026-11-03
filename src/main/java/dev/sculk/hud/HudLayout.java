package dev.sculk.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sculk.util.ConfigManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class HudLayout {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<HudElement, float[]> POSITIONS = new EnumMap<>(HudElement.class);
   private static boolean loaded;
   private static boolean dirty;

   private HudLayout() {
   }

   private static Path file() {
      return ConfigManager.directory().resolve("hud.json");
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;

         for (HudElement element : HudElement.values()) {
            POSITIONS.put(element, new float[]{element.defaultX(), element.defaultY()});
         }

         Path file = file();
         if (Files.isRegularFile(file)) {
            try {
               JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

               for (HudElement element : HudElement.values()) {
                  if (root.has(element.name())) {
                     JsonArray pair = root.getAsJsonArray(element.name());
                     if (pair.size() >= 2) {
                        float[] position = POSITIONS.get(element);
                        position[0] = clamp01(pair.get(0).getAsFloat());
                        position[1] = clamp01(pair.get(1).getAsFloat());
                     }
                  }
               }
            } catch (RuntimeException | IOException var8) {
            }

            dirty = false;
         }
      }
   }

   public static float x(HudElement element) {
      ensureLoaded();
      return POSITIONS.get(element)[0];
   }

   public static float y(HudElement element) {
      ensureLoaded();
      return POSITIONS.get(element)[1];
   }

   public static void set(HudElement element, float x, float y) {
      ensureLoaded();
      float[] position = POSITIONS.get(element);
      position[0] = clamp01(x);
      position[1] = clamp01(y);
      dirty = true;
   }

   public static void reset(HudElement element) {
      ensureLoaded();
      float[] position = POSITIONS.get(element);
      position[0] = element.defaultX();
      position[1] = element.defaultY();
      dirty = true;
   }

   public static void save() {
      ensureLoaded();
      if (dirty) {
         try {
            JsonObject root = new JsonObject();

            for (HudElement element : HudElement.values()) {
               JsonArray pair = new JsonArray(2);
               pair.add(POSITIONS.get(element)[0]);
               pair.add(POSITIONS.get(element)[1]);
               root.add(element.name(), pair);
            }

            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root));
            dirty = false;
         } catch (RuntimeException | IOException var6) {
         }
      }
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }
}
