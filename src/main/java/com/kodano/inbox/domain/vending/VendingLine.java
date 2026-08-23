package com.kodano.inbox.domain.vending;

import java.time.Instant;

public record VendingLine(Long seq, String saleId, String sku, Integer qty, Long amountMinor, String currency,
                          Instant soldAt, String payload) {
}
