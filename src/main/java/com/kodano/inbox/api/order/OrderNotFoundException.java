package com.kodano.inbox.api.order;

public class OrderNotFoundException extends RuntimeException {

   OrderNotFoundException(String orderId) {
      super("No events received for order %s".formatted(orderId));
   }
}
