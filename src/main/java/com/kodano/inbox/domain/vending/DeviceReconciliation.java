package com.kodano.inbox.domain.vending;

import java.util.List;

public record DeviceReconciliation(String deviceId, long acceptedCount, List<Long> missingSeqs) {
}
