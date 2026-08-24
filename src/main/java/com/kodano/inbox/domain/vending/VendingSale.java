package com.kodano.inbox.domain.vending;

import java.time.Instant;

public record VendingSale(String deviceId, long seq, String saleId, String sku, int qty, long amountMinor,
                          String currency, Instant soldAt) {
}
