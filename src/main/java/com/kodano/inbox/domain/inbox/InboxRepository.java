package com.kodano.inbox.domain.inbox;

public interface InboxRepository {

   boolean insertIfAbsent(InboxSubmission submission);
}
