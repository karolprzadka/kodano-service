package com.kodano.inbox.domain.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestService {

   private final InboxRepository inboxRepository;

   public IngestStatus accept(InboxSubmission submission) {
      return inboxRepository.insertIfAbsent(submission) ? IngestStatus.ACCEPTED : IngestStatus.DUPLICATE;
   }
}
