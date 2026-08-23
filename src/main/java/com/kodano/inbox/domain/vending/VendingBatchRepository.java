package com.kodano.inbox.domain.vending;

public interface VendingBatchRepository {

   void registerIfAbsent(String deviceId, String batchId, int lineCount);
}
