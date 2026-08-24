package com.kodano.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class VendingSequenceGapTest extends BaseIntegrationTest {

   private static final String DEVICE_ID = "DEV-GAP-1";
   private static final long AMOUNT_MINOR = 1000L;

   private static final List<Long> LATE_BATCH = Stream.concat(seqs(1, 41), seqs(43, 49)).toList();
   private static final List<Long> EARLY_BATCH = seqs(50, 59).toList();

   @Test
   void reportsMissingSequenceNumbersAtEveryMoment() throws Exception {
      deliverBatch("batch-late-arriving-second", EARLY_BATCH);

      Map<String, Object> afterOutOfOrderBatch = reconcile();
      assertThat(afterOutOfOrderBatch).containsEntry("status", "SEQUENCE_GAP_DETECTED");
      assertThat(afterOutOfOrderBatch).containsEntry("acceptedCount", EARLY_BATCH.size());
      assertThat(ranges(afterOutOfOrderBatch)).containsExactly(Map.of("from", 1, "to", 49));

      deliverBatch("batch-delayed", LATE_BATCH);

      Map<String, Object> afterDelayedBatch = reconcile();
      assertThat(afterDelayedBatch).containsEntry("acceptedCount", LATE_BATCH.size() + EARLY_BATCH.size());
      assertThat(ranges(afterDelayedBatch)).containsExactly(Map.of("from", 42, "to", 42));

      deliverBatch("batch-late-arriving-second", EARLY_BATCH);

      Map<String, Object> afterDuplicate = reconcile();
      assertThat(afterDuplicate).isEqualTo(afterDelayedBatch);
      assertThat(afterDuplicate).containsEntry("amountMinorChecksum",
            (int) ((LATE_BATCH.size() + EARLY_BATCH.size()) * AMOUNT_MINOR));
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> ranges(Map<String, Object> reconciliation) {
      return (List<Map<String, Object>>) reconciliation.get("missingRanges");
   }

   private void deliverBatch(String batchId, List<Long> seqs) throws Exception {
      MvcResult accepted = deliver(VENDING_TOKEN, "/api/v1/inbox/vending/batches", batch(batchId, seqs));
      assertThat(accepted.getResponse().getStatus()).isEqualTo(202);

      await().atMost(Duration.ofSeconds(20)).until(() -> unprocessedLines() == 0);
   }

   private Map<String, Object> reconcile() throws Exception {
      MvcResult result = mockMvc.perform(get("/api/v1/inbox/reconciliation")
            .param("source", "vending")
            .param("device", DEVICE_ID)).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);

      Map<String, Object> body = readBody(result);
      body.remove("deviceId");
      return body;
   }

   private static String batch(String batchId, List<Long> seqs) {
      String lines = seqs.stream()
            .map(seq -> """
                  {"seq":%d,"saleId":"sale-%d","sku":"SOCZ-1DAY-30","qty":1,"amountMinor":%d,"currency":"PLN",\
                  "soldAt":"2026-07-27T09:00:00Z"}""".formatted(seq, seq, AMOUNT_MINOR))
            .reduce((left, right) -> left + "," + right)
            .orElseThrow();
      return """
            {"deviceId":"%s","batchId":"%s","lines":[%s]}""".formatted(DEVICE_ID, batchId, lines);
   }

   private long unprocessedLines() {
      return jdbcTemplate.queryForObject("select count(*) from inbox_messages where aggregate_id = ? and status <> 'done'",
            Long.class, DEVICE_ID);
   }

   private static Stream<Long> seqs(long from, long to) {
      return LongStream.rangeClosed(from, to).boxed();
   }
}
