package com.kodano.inbox.api.deadletter;

import com.kodano.inbox.api.DeadLetterApi;
import com.kodano.inbox.api.DeadLetterDto;
import com.kodano.inbox.api.RetryResultDto;
import com.kodano.inbox.domain.inbox.DeadLetterService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class DeadLetterController implements DeadLetterApi {

   private static final String REQUEUED = "pending";

   private final DeadLetterService deadLetterService;
   private final DeadLetterDtoMapper deadLetterDtoMapper;

   @Override
   public ResponseEntity<List<DeadLetterDto>> listDeadLetters() {
      return ResponseEntity.ok(deadLetterDtoMapper.toDto(deadLetterService.parked()));
   }

   @Override
   public ResponseEntity<RetryResultDto> retryDeadLetter(UUID id) {
      if (!deadLetterService.retry(id)) {
         throw new DeadLetterNotFoundException(id);
      }
      return ResponseEntity.accepted().body(new RetryResultDto(id, REQUEUED));
   }
}
