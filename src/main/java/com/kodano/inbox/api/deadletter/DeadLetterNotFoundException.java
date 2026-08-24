package com.kodano.inbox.api.deadletter;

import java.util.UUID;

public class DeadLetterNotFoundException extends RuntimeException {

   DeadLetterNotFoundException(UUID id) {
      super("No parked message with id %s".formatted(id));
   }
}
