package com.kodano.inbox.api.reconciliation;

import com.kodano.inbox.api.DeviceReconciliationDto;
import com.kodano.inbox.api.ReconciliationApi;
import com.kodano.inbox.api.SeqRangeDto;
import com.kodano.inbox.domain.vending.DeviceReconciliation;
import com.kodano.inbox.domain.vending.ReconciliationService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ReconciliationController implements ReconciliationApi {

   private final ReconciliationService reconciliationService;

   @Override
   public ResponseEntity<DeviceReconciliationDto> getReconciliation(String source, String device, LocalDate day) {
      DeviceReconciliation reconciliation = reconciliationService.reconcile(source, device, day);
      List<SeqRangeDto> missing = reconciliation.missingRanges().stream()
            .map(range -> new SeqRangeDto(range.from(), range.to()))
            .toList();
      return ResponseEntity.ok(new DeviceReconciliationDto(reconciliation.deviceId(), status(reconciliation),
            reconciliation.acceptedCount(), reconciliation.amountMinorChecksum(), missing));
   }

   private static DeviceReconciliationDto.StatusEnum status(DeviceReconciliation reconciliation) {
      return reconciliation.complete()
            ? DeviceReconciliationDto.StatusEnum.COMPLETE
            : DeviceReconciliationDto.StatusEnum.SEQUENCE_GAP_DETECTED;
   }
}
