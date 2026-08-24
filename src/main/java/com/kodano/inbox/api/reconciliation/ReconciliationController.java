package com.kodano.inbox.api.reconciliation;

import com.kodano.inbox.api.DeviceReconciliationDto;
import com.kodano.inbox.api.ReconciliationApi;
import com.kodano.inbox.domain.vending.DeviceReconciliation;
import com.kodano.inbox.domain.vending.ReconciliationService;
import java.time.LocalDate;
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
      return ResponseEntity.ok(new DeviceReconciliationDto(reconciliation.deviceId(), reconciliation.acceptedCount(),
            reconciliation.missingSeqs()));
   }
}
