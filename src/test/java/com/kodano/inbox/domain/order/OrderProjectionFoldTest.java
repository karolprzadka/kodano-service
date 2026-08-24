package com.kodano.inbox.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderProjectionFoldTest {

   private static final String ORDER_ID = "ORD-1";
   private static final Instant PLACED_AT = Instant.parse("2026-07-27T10:00:00Z");
   private static final Instant PAID_AT = Instant.parse("2026-07-27T10:05:00Z");

   private static final OrderEvent PLACED = new OrderEvent("e1", ORDER_ID, OrderEventType.PLACED, PLACED_AT);
   private static final OrderEvent PAID = new OrderEvent("e2", ORDER_ID, OrderEventType.PAID, PAID_AT);

   @Test
   void buildsSameProjectionRegardlessOfDeliveryOrder() {
      OrderProjection inOrder = OrderProjectionFold.fold(ORDER_ID, List.of(PLACED, PAID)).projection();
      OrderProjection reversed = OrderProjectionFold.fold(ORDER_ID, List.of(PAID, PLACED)).projection();

      assertThat(reversed).isEqualTo(inOrder);
      assertThat(inOrder.status()).isEqualTo(OrderStatus.PAID);
      assertThat(inOrder.placedAt()).isEqualTo(PLACED_AT);
      assertThat(inOrder.lastEventAt()).isEqualTo(PAID_AT);
   }

   @Test
   void staleCancellationDoesNotRollBackStatus() {
      OrderEvent staleCancelled = new OrderEvent("e3", ORDER_ID, OrderEventType.CANCELLED,
            Instant.parse("2026-07-27T10:02:00Z"));

      OrderFold fold = OrderProjectionFold.fold(ORDER_ID, List.of(PLACED, PAID, staleCancelled));

      assertThat(fold.projection().status()).isEqualTo(OrderStatus.PAID);
      assertThat(fold.projection().cancelledAt()).isEqualTo(staleCancelled.occurredAt());
      assertThat(fold.projection().lastEventAt()).isEqualTo(PAID_AT);
   }

   @Test
   void cancellationWinsWhenItIsTheNewestEvent() {
      OrderEvent cancelled = new OrderEvent("e4", ORDER_ID, OrderEventType.CANCELLED,
            Instant.parse("2026-07-27T10:03:00Z"));

      OrderFold fold = OrderProjectionFold.fold(ORDER_ID, List.of(cancelled, PLACED));

      assertThat(fold.projection().status()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(fold.effectOf(cancelled.eventId())).isEqualTo(EventEffect.APPLIED);
   }

   @Test
   void ignoresDuplicatedDeliveriesOfTheSameEvent() {
      OrderProjection once = OrderProjectionFold.fold(ORDER_ID, List.of(PLACED, PAID)).projection();
      OrderProjection twice = OrderProjectionFold.fold(ORDER_ID, List.of(PLACED, PAID, PAID, PLACED)).projection();

      assertThat(twice).isEqualTo(once);
   }

   @Test
   void reportsNothingForAnOrderWithoutEvents() {
      OrderFold fold = OrderProjectionFold.fold(ORDER_ID, Collections.emptyList());

      assertThat(fold.projection().status()).isNull();
      assertThat(fold.appliedEventIds()).isEmpty();
   }
}
