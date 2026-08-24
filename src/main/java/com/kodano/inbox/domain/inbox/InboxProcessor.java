package com.kodano.inbox.domain.inbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class InboxProcessor {

   private static final String NO_HANDLER = "No handler registered for aggregate type %s";
   private static final String PARKED_LOG = "Message {} from {} parked after {} attempts: {}";

   private final InboxRepository inboxRepository;
   private final InboxProperties inboxProperties;
   private final Map<AggregateType, MessageHandler> handlers;

   public InboxProcessor(InboxRepository inboxRepository, InboxProperties inboxProperties, List<MessageHandler> handlers) {
      this.inboxRepository = inboxRepository;
      this.inboxProperties = inboxProperties;
      this.handlers = handlers.stream().collect(Collectors.toMap(MessageHandler::aggregateType, Function.identity()));
   }

   @Transactional
   public boolean processNext() {
      Optional<InboxMessage> claimed = inboxRepository.claimNext();
      claimed.ifPresent(this::apply);
      return claimed.isPresent();
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public boolean recordFailure(ProcessingFailedException failure) {
      InboxMessage message = failure.message();
      Throwable cause = failure.getCause();
      int attempts = message.attempts() + 1;
      if (attempts >= inboxProperties.maxAttempts()) {
         log.warn(PARKED_LOG, message.id(), message.sourceCode(), attempts, cause.getMessage());
         inboxRepository.markDead(message.id(), describe(cause));
      } else {
         inboxRepository.markForRetry(message.id(), describe(cause), nextAttemptAt(attempts));
      }
      return true;
   }

   private static String describe(Throwable cause) {
      return cause.getClass().getSimpleName() + ": " + cause.getMessage();
   }

   private void apply(InboxMessage message) {
      MessageHandler handler = handlers.get(message.aggregateType());
      if (handler == null) {
         throw new ProcessingFailedException(message,
               new IllegalStateException(NO_HANDLER.formatted(message.aggregateType())));
      }
      try {
         handler.handle(message);
      } catch (RuntimeException cause) {
         throw new ProcessingFailedException(message, cause);
      }
      inboxRepository.markDone(message.id());
   }

   private Instant nextAttemptAt(int attempts) {
      return Instant.now().plusMillis(inboxProperties.backoff().toMillis() * (1L << attempts));
   }
}
