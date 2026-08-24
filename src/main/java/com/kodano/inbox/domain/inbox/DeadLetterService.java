package com.kodano.inbox.domain.inbox;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeadLetterService {

   private final InboxRepository inboxRepository;

   public List<DeadLetter> parked() {
      return inboxRepository.findDead();
   }

   @Transactional
   public boolean retry(UUID id) {
      return inboxRepository.requeue(id);
   }
}
