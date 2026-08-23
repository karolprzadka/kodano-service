package com.kodano.inbox.domain.inbox;

public record InboxSubmission(String sourceCode, String externalId, AggregateType aggregateType, String aggregateId,
                              String payload) {
}
