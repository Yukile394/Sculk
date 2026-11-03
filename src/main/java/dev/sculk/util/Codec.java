package dev.sculk.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Codec {
   private Codec() {
   }

   public static String d(String encoded) {
      byte[] payload = Base64.getDecoder().decode(encoded);
      int seed = (payload[0] & 255) << 24 | (payload[1] & 255) << 16 | (payload[2] & 255) << 8 | payload[3] & 255;
      int x = seed;
      byte[] out = new byte[payload.length - 4];

      for (int i = 0; i < out.length; i++) {
         int var6 = x ^ x << 13 ^ x >>> 7;
         x = var6 ^ var6 << 17 ^ var6 >>> 5;
         out[i] = (byte)(payload[4 + i] ^ x & 0xFF);
      }

      return new String(out, StandardCharsets.UTF_8);
   }
}
