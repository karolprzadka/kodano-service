package com.kodano.inbox.domain.vending;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodano.inbox.domain.inbox.AggregateType;
import com.kodano.inbox.domain.inbox.InboxMessage;
import com.kodano.inbox.domain.inbox.MessageHandler;
import io.vavr.control.Try;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class VendingSaleHandler implements MessageHandler {

   private final ObjectMapper objectMapper;
   private final VendingSaleRepository vendingSaleRepository;

   @Override
   public AggregateType aggregateType() {
      return AggregateType.DEVICE;
   }

   @Override
   public void handle(InboxMessage message) {
      RawLine line = Try.of(() -> objectMapper.readValue(message.payload(), RawLine.class)).get();
      vendingSaleRepository.insertIfAbsent(new VendingSale(message.aggregateId(), line.seq(), line.saleId(), line.sku(),
            line.qty(), line.amountMinor(), line.currency(), line.soldAt()));
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   private record RawLine(long seq, String saleId, String sku, int qty, long amountMinor, String currency,
                          Instant soldAt) {
   }
}
