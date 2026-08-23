package com.kodano.inbox.domain.inbox;

public interface MessageHandler {

   AggregateType aggregateType();

   void handle(InboxMessage message);
}
