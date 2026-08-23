package com.kodano.inbox.domain.inbox;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InboxRepository {

   boolean insertIfAbsent(InboxSubmission submission);

   Optional<InboxMessage> claimNext();

   void markDone(UUID id);

   void markForRetry(UUID id, String error, Instant nextAttemptAt);

   void markDead(UUID id, String reasonCode, String detail);
}
