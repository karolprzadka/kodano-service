package com.kodano.inbox.domain.order;

import java.time.Instant;

public record OrderEvent(String eventId, String orderId, OrderEventType type, Instant occurredAt) {
}
