package com.kodano.inbox.domain.inbox;

import java.util.UUID;

public record InboxMessage(UUID id, String sourceCode, String externalId, AggregateType aggregateType,
                           String aggregateId, String payload, int attempts) {
}
