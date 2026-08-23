package com.kodano.inbox.domain.vending;

import com.kodano.inbox.domain.inbox.AggregateType;
import com.kodano.inbox.domain.inbox.IngestService;
import com.kodano.inbox.domain.inbox.InboxSubmission;
import io.vavr.control.Option;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendingIngestService {

   private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
   private static final String MISSING_SEQ = "seq is required and must be positive";
   private static final String MISSING_SALE_ID = "saleId is required";
   private static final String MISSING_SKU = "sku is required";
   private static final String INVALID_QTY = "qty must be positive";
   private static final String INVALID_AMOUNT = "amountMinor must not be negative";
   private static final String INVALID_CURRENCY = "currency must be a three letter code";
   private static final String MISSING_SOLD_AT = "soldAt is required";

   private final IngestService ingestService;
   private final VendingBatchRepository vendingBatchRepository;

   @Transactional
   public List<LineResult> accept(VendingBatch batch) {
      vendingBatchRepository.registerIfAbsent(batch.deviceId(), batch.batchId(), batch.lines().size());
      return batch.lines().stream()
            .map(line -> accept(batch, line))
            .toList();
   }

   private LineResult accept(VendingBatch batch, VendingLine line) {
      return rejection(line)
            .map(reason -> LineResult.rejected(line.seq(), reason))
            .getOrElse(() -> LineResult.of(line.seq(), ingestService.accept(submission(batch, line))));
   }

   private InboxSubmission submission(VendingBatch batch, VendingLine line) {
      return new InboxSubmission(batch.sourceCode(), batch.deviceId() + ":" + line.seq(), AggregateType.DEVICE,
            batch.deviceId(), line.payload());
   }

   private static Option<String> rejection(VendingLine line) {
      if (line.seq() == null || line.seq() < 1) {
         return Option.of(MISSING_SEQ);
      }
      if (line.saleId() == null || line.saleId().isBlank()) {
         return Option.of(MISSING_SALE_ID);
      }
      if (line.sku() == null || line.sku().isBlank()) {
         return Option.of(MISSING_SKU);
      }
      if (line.qty() == null || line.qty() < 1) {
         return Option.of(INVALID_QTY);
      }
      if (line.amountMinor() == null || line.amountMinor() < 0) {
         return Option.of(INVALID_AMOUNT);
      }
      if (line.currency() == null || !CURRENCY.matcher(line.currency()).matches()) {
         return Option.of(INVALID_CURRENCY);
      }
      if (line.soldAt() == null) {
         return Option.of(MISSING_SOLD_AT);
      }
      return Option.none();
   }
}
