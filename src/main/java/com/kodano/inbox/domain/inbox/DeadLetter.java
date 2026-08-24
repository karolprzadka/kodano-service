package com.kodano.inbox.domain.inbox;

import java.time.Instant;
import java.util.UUID;

public record DeadLetter(UUID id, String sourceCode, String externalId, AggregateType aggregateType, String aggregateId,
                         int attempts, String lastError, Instant receivedAt) {
}
