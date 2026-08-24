package com.kodano.inbox.domain.vending;

import java.util.List;

public record DeviceReconciliation(String deviceId, long acceptedCount, long amountMinorChecksum,
                                   List<SeqRange> missingRanges) {

   public boolean complete() {
      return missingRanges.isEmpty();
   }
}
