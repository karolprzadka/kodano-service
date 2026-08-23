package com.kodano.inbox.infrastructure.worker;

import com.kodano.inbox.domain.inbox.InboxProcessor;
import com.kodano.inbox.domain.inbox.InboxProperties;
import com.kodano.inbox.domain.inbox.ProcessingFailedException;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InboxPoller {

   private static final String POLL_FAILED = "Inbox poll failed";

   private final InboxProcessor inboxProcessor;
   private final InboxProperties inboxProperties;

   @Scheduled(fixedDelayString = "${inbox.poll-interval}")
   void poll() {
      int drained = 0;
      while (drained < inboxProperties.batchSize() && processNext()) {
         drained++;
      }
   }

   private boolean processNext() {
      return Try.ofCallable(inboxProcessor::processNext)
            .recover(ProcessingFailedException.class, inboxProcessor::recordFailure)
            .onFailure(cause -> log.error(POLL_FAILED, cause))
            .getOrElse(false);
   }
}
