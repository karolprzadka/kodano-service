package com.kodano.inbox.api.vending;

import com.kodano.inbox.api.VendingBatchDto;
import com.kodano.inbox.api.VendingBatchResultDto;
import com.kodano.inbox.api.VendingBatchesApi;
import com.kodano.inbox.domain.vending.LineResult;
import com.kodano.inbox.domain.vending.VendingIngestService;
import com.kodano.inbox.infrastructure.security.RequestSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class VendingBatchesController implements VendingBatchesApi {

   private final VendingIngestService vendingIngestService;
   private final VendingBatchMapper vendingBatchMapper;
   private final RequestSource requestSource;

   @Override
   public ResponseEntity<VendingBatchResultDto> ingestVendingBatch(VendingBatchDto batch) {
      List<LineResult> results = vendingIngestService.accept(vendingBatchMapper.toBatch(requestSource.current(), batch));
      return ResponseEntity.accepted().body(vendingBatchMapper.toResult(batch, results));
   }
}
