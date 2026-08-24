package com.kodano.inbox.domain.vending;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

   private static final String VENDING = "vending";

   private final VendingSaleRepository vendingSaleRepository;

   public DeviceReconciliation reconcile(String source, String deviceId, LocalDate day) {
      if (!VENDING.equals(source)) {
         throw new UnsupportedSourceException(source);
      }
      DeviceTotals totals = vendingSaleRepository.totals(deviceId, day);
      return new DeviceReconciliation(deviceId, totals.acceptedCount(), totals.amountMinorChecksum(),
            vendingSaleRepository.findMissingRanges(deviceId));
   }
}
