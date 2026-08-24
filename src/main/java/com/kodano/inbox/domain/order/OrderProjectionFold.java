package com.kodano.inbox.domain.order;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OrderProjectionFold {

   private static final Comparator<OrderEvent> BY_OCCURRENCE =
         Comparator.comparing(OrderEvent::occurredAt).thenComparing(OrderEvent::eventId);

   private OrderProjectionFold() {
   }

   public static OrderFold fold(String orderId, Collection<OrderEvent> events) {
      OrderStatus status = null;
      Instant placedAt = null;
      Instant paidAt = null;
      Instant cancelledAt = null;
      Instant refundedAt = null;
      Instant lastEventAt = null;
      Set<String> applied = new LinkedHashSet<>();

      for (OrderEvent event : distinctSorted(events)) {
         switch (event.type()) {
            case PLACED -> placedAt = event.occurredAt();
            case PAID -> paidAt = event.occurredAt();
            case CANCELLED -> cancelledAt = event.occurredAt();
            case REFUNDED -> refundedAt = event.occurredAt();
         }
         if (allows(status, event.type())) {
            status = OrderStatus.valueOf(event.type().name());
            lastEventAt = event.occurredAt();
            applied.add(event.eventId());
         }
      }

      return new OrderFold(new OrderProjection(orderId, status, placedAt, paidAt, cancelledAt, refundedAt, lastEventAt),
            applied);
   }

   private static List<OrderEvent> distinctSorted(Collection<OrderEvent> events) {
      return events.stream()
            .collect(Collectors.toMap(OrderEvent::eventId, Function.identity(), (first, ignored) -> first, LinkedHashMap::new))
            .values().stream()
            .sorted(BY_OCCURRENCE)
            .toList();
   }

   private static boolean allows(OrderStatus current, OrderEventType next) {
      return switch (next) {
         case PLACED -> current == null;
         case PAID -> current == null || current == OrderStatus.PLACED || current == OrderStatus.CANCELLED;
         case CANCELLED -> current == null || current == OrderStatus.PLACED;
         case REFUNDED -> current == OrderStatus.PAID;
      };
   }
}
