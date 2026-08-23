package com.kodano.inbox.domain.vending;

import java.util.List;

public record VendingBatch(String sourceCode, String deviceId, String batchId, List<VendingLine> lines) {
}
