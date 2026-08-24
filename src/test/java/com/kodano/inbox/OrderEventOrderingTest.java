package com.kodano.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class OrderEventOrderingTest extends BaseIntegrationTest {

   private static final String PLACED = """
         {"eventId":"%1$s-placed","orderId":"%1$s","type":"order.placed","occurredAt":"2026-07-27T10:00:00Z"}""";
   private static final String PAID = """
         {"eventId":"%1$s-paid","orderId":"%1$s","type":"order.paid","occurredAt":"2026-07-27T10:05:00Z"}""";
   private static final String STALE_CANCELLED = """
         {"eventId":"%1$s-cancelled","orderId":"%1$s","type":"order.cancelled","occurredAt":"2026-07-27T10:02:00Z"}""";

   private static final List<List<String>> DELIVERY_ORDERS = List.of(
         List.of(PLACED, PAID, STALE_CANCELLED),
         List.of(PAID, PLACED, STALE_CANCELLED),
         List.of(STALE_CANCELLED, PAID, PLACED),
         List.of(PAID, STALE_CANCELLED, PLACED));

   @Test
   void buildsTheSameProjectionForEveryDeliveryOrder() throws Exception {
      List<Map<String, Object>> projections = new ArrayList<>();
      for (List<String> deliveryOrder : DELIVERY_ORDERS) {
         projections.add(deliverAndRead(deliveryOrder));
      }

      assertThat(projections).allSatisfy(projection -> {
         assertThat(projection).containsEntry("status", "PAID");
         assertThat((String) projection.get("placedAt")).startsWith("2026-07-27T10:00");
         assertThat((String) projection.get("paidAt")).startsWith("2026-07-27T10:05");
         assertThat((String) projection.get("cancelledAt")).startsWith("2026-07-27T10:02");
         assertThat(projection.get("lastEventAt")).isEqualTo(projection.get("paidAt"));
      });
      assertThat(projections).containsOnly(projections.getFirst());
   }

   private Map<String, Object> deliverAndRead(List<String> deliveryOrder) throws Exception {
      String orderId = "ORD-ORDER-" + DELIVERY_ORDERS.indexOf(deliveryOrder);
      for (String event : deliveryOrder) {
         MvcResult accepted = deliver(MAGENTO_TOKEN, "/api/v1/inbox/magento/events", event.formatted(orderId));
         assertThat(accepted.getResponse().getStatus()).isEqualTo(202);
      }

      await().atMost(Duration.ofSeconds(10)).until(() -> processedEvents(orderId) == deliveryOrder.size());

      MvcResult projection = mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)).andReturn();
      assertThat(projection.getResponse().getStatus()).isEqualTo(200);

      Map<String, Object> body = readBody(projection);
      body.remove("orderId");
      return body;
   }

   private long processedEvents(String orderId) {
      return jdbcTemplate.queryForObject(
            "select count(*) from inbox_messages where aggregate_id = ? and status = 'done'", Long.class, orderId);
   }
}
