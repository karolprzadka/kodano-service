package com.kodano.inbox.domain.order;

import java.time.Instant;

public record OrderProjection(String orderId, OrderStatus status, Instant placedAt, Instant paidAt, Instant cancelledAt,
                              Instant refundedAt, Instant lastEventAt) {
}
