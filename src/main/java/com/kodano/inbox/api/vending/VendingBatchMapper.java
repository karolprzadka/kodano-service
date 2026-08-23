package com.kodano.inbox.api.vending;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodano.inbox.api.IngestStatusDto;
import com.kodano.inbox.api.VendingBatchDto;
import com.kodano.inbox.api.VendingBatchResultDto;
import com.kodano.inbox.api.VendingLineResultDto;
import com.kodano.inbox.api.VendingSaleLineDto;
import com.kodano.inbox.domain.vending.LineResult;
import com.kodano.inbox.domain.vending.VendingBatch;
import com.kodano.inbox.domain.vending.VendingLine;
import io.vavr.control.Try;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class VendingBatchMapper {

   private final ObjectMapper objectMapper;

   VendingBatch toBatch(String sourceCode, VendingBatchDto batch) {
      List<VendingLine> lines = batch.getLines().stream().map(this::toLine).toList();
      return new VendingBatch(sourceCode, batch.getDeviceId(), batch.getBatchId(), lines);
   }

   VendingBatchResultDto toResult(VendingBatchDto batch, List<LineResult> results) {
      return new VendingBatchResultDto(batch.getDeviceId(), batch.getBatchId(),
            results.stream().map(VendingBatchMapper::toLineResult).toList());
   }

   private VendingLine toLine(VendingSaleLineDto line) {
      Instant soldAt = Optional.ofNullable(line.getSoldAt()).map(OffsetDateTime::toInstant).orElse(null);
      return new VendingLine(line.getSeq(), line.getSaleId(), line.getSku(), line.getQty(), line.getAmountMinor(),
            line.getCurrency(), soldAt, Try.of(() -> objectMapper.writeValueAsString(line)).get());
   }

   private static VendingLineResultDto toLineResult(LineResult result) {
      VendingLineResultDto dto = new VendingLineResultDto(result.seq(), IngestStatusDto.valueOf(result.status().name()));
      dto.setReason(result.reason());
      return dto;
   }
}
