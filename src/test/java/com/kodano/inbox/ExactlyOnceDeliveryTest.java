package com.kodano.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.control.Try;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ExactlyOnceDeliveryTest extends BaseIntegrationTest {

   private static final int CONCURRENT_DELIVERIES = 20;

   @Test
   void storesRepeatedMagentoEventOnce() throws Exception {
      String eventId = "evt-concurrent-1";
      String body = """
            {"eventId":"%s","orderId":"ORD-CONCURRENT-1","type":"order.placed","occurredAt":"2026-07-27T10:00:00Z"}"""
            .formatted(eventId);

      List<String> statuses = deliverInParallel(
            () -> deliver(MAGENTO_TOKEN, "/api/v1/inbox/magento/events", body), "$.status");

      assertThat(statuses).filteredOn("ACCEPTED"::equals).hasSize(1);
      assertThat(statuses).filteredOn("DUPLICATE"::equals).hasSize(CONCURRENT_DELIVERIES - 1);
      assertThat(countInbox("magento", eventId)).isOne();
   }

   @Test
   void storesRepeatedVendingBatchOnce() throws Exception {
      String deviceId = "DEV-CONCURRENT-1";
      String body = """
            {"deviceId":"%s","batchId":"batch-1","lines":[
               {"seq":1,"saleId":"s-1","sku":"SOCZ-1DAY-30","qty":1,"amountMinor":12900,"currency":"PLN","soldAt":"2026-07-27T09:00:00Z"},
               {"seq":2,"saleId":"s-2","sku":"PLYN-360","qty":2,"amountMinor":4900,"currency":"PLN","soldAt":"2026-07-27T09:05:00Z"}]}"""
            .formatted(deviceId);

      List<String> firstLineStatuses = deliverInParallel(
            () -> deliver(VENDING_TOKEN, "/api/v1/inbox/vending/batches", body), "$.lines[0].status");

      assertThat(firstLineStatuses).filteredOn("ACCEPTED"::equals).hasSize(1);
      assertThat(countInbox("vending", deviceId + ":1")).isOne();
      assertThat(countBatches(deviceId)).isOne();
   }

   private List<String> deliverInParallel(Delivery delivery, String jsonPath) throws Exception {
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_DELIVERIES)) {
         List<Future<String>> deliveries = IntStream.range(0, CONCURRENT_DELIVERIES)
               .mapToObj(attempt -> executor.<String>submit(() -> {
                  start.await(5, TimeUnit.SECONDS);
                  MvcResult result = delivery.execute();
                  assertThat(result.getResponse().getStatus()).isEqualTo(202);
                  return readString(result, jsonPath);
               }))
               .toList();
         start.countDown();
         return deliveries.stream().map(future -> Try.of(future::get).get()).toList();
      }
   }

   private long countInbox(String sourceCode, String externalId) {
      return jdbcTemplate.queryForObject(
            "select count(*) from inbox_messages where source_code = ? and external_id = ?",
            Long.class, sourceCode, externalId);
   }

   private long countBatches(String deviceId) {
      return jdbcTemplate.queryForObject("select count(*) from vending_batches where device_id = ?", Long.class, deviceId);
   }

   @FunctionalInterface
   private interface Delivery {

      MvcResult execute() throws Exception;
   }
}
