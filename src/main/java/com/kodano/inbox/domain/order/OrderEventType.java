package com.kodano.inbox.domain.order;

import java.util.Arrays;

public enum OrderEventType {
   PLACED("order.placed"),
   PAID("order.paid"),
   CANCELLED("order.cancelled"),
   REFUNDED("order.refunded");

   private static final String UNKNOWN_TYPE = "Unknown magento event type: %s";

   private final String wireName;

   OrderEventType(String wireName) {
      this.wireName = wireName;
   }

   public static OrderEventType fromWireName(String wireName) {
      return Arrays.stream(values())
            .filter(type -> type.wireName.equals(wireName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(UNKNOWN_TYPE.formatted(wireName)));
   }
}
