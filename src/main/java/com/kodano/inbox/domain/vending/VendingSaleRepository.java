package com.kodano.inbox.domain.vending;

import java.time.LocalDate;
import java.util.List;

public interface VendingSaleRepository {

   void insertIfAbsent(VendingSale sale);

   DeviceTotals totals(String deviceId, LocalDate day);

   List<SeqRange> findMissingRanges(String deviceId);
}
