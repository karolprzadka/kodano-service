package com.kodano.inbox.api.magento;

import com.kodano.inbox.api.IngestStatusDto;
import com.kodano.inbox.api.MagentoEventDto;
import com.kodano.inbox.api.MagentoEventResultDto;
import com.kodano.inbox.api.MagentoEventsApi;
import com.kodano.inbox.domain.inbox.IngestService;
import com.kodano.inbox.domain.inbox.IngestStatus;
import com.kodano.inbox.infrastructure.security.RequestSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class MagentoEventsController implements MagentoEventsApi {

   private final IngestService ingestService;
   private final MagentoEventMapper magentoEventMapper;
   private final RequestSource requestSource;

   @Override
   public ResponseEntity<MagentoEventResultDto> ingestMagentoEvent(MagentoEventDto event) {
      IngestStatus status = ingestService.accept(magentoEventMapper.toSubmission(requestSource.current(), event));
      return ResponseEntity.accepted().body(new MagentoEventResultDto(event.getEventId(), IngestStatusDto.valueOf(status.name())));
   }
}
