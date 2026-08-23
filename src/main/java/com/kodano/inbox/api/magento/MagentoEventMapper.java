package com.kodano.inbox.api.magento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodano.inbox.api.MagentoEventDto;
import com.kodano.inbox.domain.inbox.AggregateType;
import com.kodano.inbox.domain.inbox.InboxSubmission;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MagentoEventMapper {

   private final ObjectMapper objectMapper;

   InboxSubmission toSubmission(String sourceCode, MagentoEventDto event) {
      return new InboxSubmission(sourceCode, event.getEventId(), AggregateType.ORDER, event.getOrderId(),
            Try.of(() -> objectMapper.writeValueAsString(event)).get());
   }
}
