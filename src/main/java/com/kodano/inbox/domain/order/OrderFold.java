package com.kodano.inbox.domain.order;

import java.util.Set;

public record OrderFold(OrderProjection projection, Set<String> appliedEventIds) {

   public EventEffect effectOf(String eventId) {
      return appliedEventIds.contains(eventId) ? EventEffect.APPLIED : EventEffect.IGNORED;
   }
}
