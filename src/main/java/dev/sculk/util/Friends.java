package dev.sculk.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class Friends {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = ConfigManager.directory().resolve("friends.json");
   private static final int MAX_LENGTH = 16;
   private static final Map<String, String> NAMES = new LinkedHashMap<>();

   private Friends() {
   }

   public static boolean isAlly(Entity entity) {
      if (entity instanceof PlayerEntity player && NAMES.containsKey(key(player.getName().getString()))) {
         return true;
      }

      return false;
   }

   public static boolean contains(String name) {
      return NAMES.containsKey(key(name));
   }

   public static boolean isAddable(String name) {
      String trimmed = name == null ? "" : name.trim();
      return !trimmed.isEmpty() && key(trimmed).length() <= 16;
   }

   public static List<String> names() {
      return new ArrayList<>(NAMES.values());
   }

   public static boolean add(String raw) {
      String name = raw == null ? "" : raw.trim();
      if (!isAddable(name)) {
         return false;
      } else if (NAMES.putIfAbsent(key(name), name) == null) {
         save();
         return true;
      } else {
         return false;
      }
   }

   public static boolean remove(String raw) {
      if (NAMES.remove(key(raw == null ? "" : raw.trim())) != null) {
         save();
         return true;
      } else {
         return false;
      }
   }

   public static boolean toggle(String name) {
      return contains(name) ? remove(name) : add(name);
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   public static void save() {
      try {
         JsonArray array = new JsonArray();

         for (String name : NAMES.values()) {
            array.add(name);
         }

         JsonObject root = new JsonObject();
         root.add("friends", array);
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, GSON.toJson(root));
      } catch (RuntimeException | IOException var3) {
      }
   }

   private static void load() {
      if (Files.isRegularFile(FILE)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (!root.has("friends")) {
               return;
            }

            for (JsonElement element : root.getAsJsonArray("friends")) {
               String name = element.getAsString();
               if (isAddable(name)) {
                  NAMES.putIfAbsent(key(name), name.trim());
               }
            }
         } catch (RuntimeException | IOException var4) {
         }
      }
   }

   static {
      load();
   }
}
