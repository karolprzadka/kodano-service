create index idx_inbox_messages_claim on inbox_messages (next_attempt_at, received_at) where status = 'pending';
