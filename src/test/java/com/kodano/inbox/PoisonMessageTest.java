package com.kodano.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class PoisonMessageTest extends BaseIntegrationTest {

   private static final String POISON_ORDER = "ORD-POISON";
   private static final String HEALTHY_ORDER = "ORD-HEALTHY";
   private static final String POISON_EVENT_ID = "evt-poison";

   private static final String EVENT = """
         {"eventId":"%s","orderId":"%s","type":"%s","occurredAt":"2026-07-27T10:00:00Z"}""";

   @Test
   void parksPoisonMessageWithoutBlockingOtherAggregates() throws Exception {
      storeUnprocessableMessage();

      MvcResult healthy = deliver(MAGENTO_TOKEN, "/api/v1/inbox/magento/events",
            EVENT.formatted("evt-healthy", HEALTHY_ORDER, "order.placed"));
      assertThat(healthy.getResponse().getStatus()).isEqualTo(202);

      await().atMost(Duration.ofSeconds(20)).until(() -> "dead".equals(statusOf(POISON_EVENT_ID)));

      assertThat(attemptsOf(POISON_EVENT_ID)).isEqualTo(3);
      assertThat(lastErrorOf(POISON_EVENT_ID)).contains("Unknown magento event type");
      assertThat(statusOf("evt-healthy")).isEqualTo("done");
      assertThat(deadLetters()).extracting(entry -> entry.get("externalId")).contains(POISON_EVENT_ID);

      retryAfterFixingTheCause();

      await().atMost(Duration.ofSeconds(20)).until(() -> "done".equals(statusOf(POISON_EVENT_ID)));
      assertThat(projectionStatus(POISON_ORDER)).isEqualTo("PLACED");
   }

   private void storeUnprocessableMessage() {
      jdbcTemplate.update("""
            insert into inbox_messages (id, source_code, external_id, aggregate_type, aggregate_id, payload)
            values (?, 'magento', ?, 'ORDER', ?, cast(? as jsonb))
            """, UUID.randomUUID(), POISON_EVENT_ID, POISON_ORDER,
            EVENT.formatted(POISON_EVENT_ID, POISON_ORDER, "order.exploded"));
   }

   private void retryAfterFixingTheCause() throws Exception {
      jdbcTemplate.update("update inbox_messages set payload = cast(? as jsonb) where external_id = ?",
            EVENT.formatted(POISON_EVENT_ID, POISON_ORDER, "order.placed"), POISON_EVENT_ID);

      UUID id = jdbcTemplate.queryForObject("select id from inbox_messages where external_id = ?", UUID.class,
            POISON_EVENT_ID);
      MvcResult retried = mockMvc.perform(post("/api/v1/inbox/dead-letter/{id}/retry", id)).andReturn();
      assertThat(retried.getResponse().getStatus()).isEqualTo(202);
   }

   private List<Map<String, Object>> deadLetters() throws Exception {
      MvcResult result = mockMvc.perform(get("/api/v1/inbox/dead-letter")).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$");
   }

   private String statusOf(String externalId) {
      return jdbcTemplate.queryForObject("select status from inbox_messages where external_id = ?", String.class,
            externalId);
   }

   private int attemptsOf(String externalId) {
      return jdbcTemplate.queryForObject("select attempts from inbox_messages where external_id = ?", Integer.class,
            externalId);
   }

   private String lastErrorOf(String externalId) {
      return jdbcTemplate.queryForObject("select last_error from inbox_messages where external_id = ?", String.class,
            externalId);
   }

   private String projectionStatus(String orderId) {
      return jdbcTemplate.queryForObject("select status from orders_projection where order_id = ?", String.class,
            orderId);
   }
}
