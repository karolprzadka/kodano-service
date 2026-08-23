package com.kodano.inbox.domain.inbox;

public class ProcessingFailedException extends RuntimeException {

   private final transient InboxMessage message;

   public ProcessingFailedException(InboxMessage message, Throwable cause) {
      super(cause);
      this.message = message;
   }

   public InboxMessage message() {
      return message;
   }
}
