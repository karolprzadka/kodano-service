drop table dead_letters;

alter table inbox_messages drop constraint chk_inbox_messages_status;
alter table inbox_messages add constraint chk_inbox_messages_status check (status in ('pending', 'done', 'dead'));
