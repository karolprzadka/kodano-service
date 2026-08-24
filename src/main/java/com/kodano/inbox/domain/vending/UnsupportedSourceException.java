package com.kodano.inbox.domain.vending;

public class UnsupportedSourceException extends RuntimeException {

   UnsupportedSourceException(String source) {
      super("Source %s does not report sequence numbers".formatted(source));
   }
}
