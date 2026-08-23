package com.kodano.inbox.infrastructure.security;

import io.vavr.control.Try;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class TokenHasher {

   private static final String ALGORITHM = "SHA-256";

   private TokenHasher() {
   }

   static String sha256(String token) {
      return Try.of(() -> MessageDigest.getInstance(ALGORITHM))
            .map(digest -> digest.digest(token.getBytes(StandardCharsets.UTF_8)))
            .map(HexFormat.of()::formatHex)
            .get();
   }
}
