package com.kodano.inbox.infrastructure.database.inbox;

import com.kodano.inbox.domain.inbox.AggregateType;
import com.kodano.inbox.domain.inbox.DeadLetter;
import com.kodano.inbox.domain.inbox.InboxMessage;
import com.kodano.inbox.domain.inbox.InboxRepository;
import com.kodano.inbox.domain.inbox.InboxSubmission;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class InboxJdbcRepository implements InboxRepository {

   private static final String INSERT_IF_ABSENT = """
         insert into inbox_messages (id, source_code, external_id, aggregate_type, aggregate_id, payload)
         values (:id, :sourceCode, :externalId, :aggregateType, :aggregateId, cast(:payload as jsonb))
         on conflict (source_code, external_id) do nothing
         """;

   private static final String CLAIM_NEXT = """
         select id, source_code, external_id, aggregate_type, aggregate_id, payload::text as payload, attempts
         from inbox_messages
         where status = 'pending'
           and next_attempt_at <= now()
           and pg_try_advisory_xact_lock(hashtext(aggregate_type || ':' || aggregate_id))
         order by received_at
         limit 1
         for update skip locked
         """;

   private static final String MARK_DONE = """
         update inbox_messages
         set status = 'done', processed_at = now(), last_error = null
         where id = :id
         """;

   private static final String MARK_FOR_RETRY = """
         update inbox_messages
         set attempts = attempts + 1, next_attempt_at = :nextAttemptAt, last_error = :error
         where id = :id
         """;

   private static final String MARK_DEAD = """
         update inbox_messages
         set status = 'dead', attempts = attempts + 1, last_error = :error
         where id = :id
         """;

   private static final String FIND_DEAD = """
         select id, source_code, external_id, aggregate_type, aggregate_id, attempts, last_error, received_at
         from inbox_messages
         where status = 'dead'
         order by received_at
         """;

   private static final String REQUEUE = """
         update inbox_messages
         set status = 'pending', attempts = 0, next_attempt_at = now(), last_error = null
         where id = :id and status = 'dead'
         """;

   private final NamedParameterJdbcTemplate jdbcTemplate;

   @Override
   public boolean insertIfAbsent(InboxSubmission submission) {
      MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("sourceCode", submission.sourceCode())
            .addValue("externalId", submission.externalId())
            .addValue("aggregateType", submission.aggregateType().name())
            .addValue("aggregateId", submission.aggregateId())
            .addValue("payload", submission.payload());
      return jdbcTemplate.update(INSERT_IF_ABSENT, parameters) == 1;
   }

   @Override
   public Optional<InboxMessage> claimNext() {
      return jdbcTemplate.query(CLAIM_NEXT, InboxJdbcRepository::toMessage).stream().findFirst();
   }

   @Override
   public void markDone(UUID id) {
      jdbcTemplate.update(MARK_DONE, new MapSqlParameterSource("id", id));
   }

   @Override
   public void markForRetry(UUID id, String error, Instant nextAttemptAt) {
      jdbcTemplate.update(MARK_FOR_RETRY, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("error", error)
            .addValue("nextAttemptAt", java.sql.Timestamp.from(nextAttemptAt)));
   }

   @Override
   public void markDead(UUID id, String error) {
      jdbcTemplate.update(MARK_DEAD, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("error", error));
   }

   @Override
   public List<DeadLetter> findDead() {
      return jdbcTemplate.query(FIND_DEAD, InboxJdbcRepository::toDeadLetter);
   }

   @Override
   public boolean requeue(UUID id) {
      return jdbcTemplate.update(REQUEUE, new MapSqlParameterSource("id", id)) == 1;
   }

   private static DeadLetter toDeadLetter(ResultSet row, int rowNumber) throws SQLException {
      return new DeadLetter(row.getObject("id", UUID.class),
            row.getString("source_code"),
            row.getString("external_id"),
            AggregateType.valueOf(row.getString("aggregate_type")),
            row.getString("aggregate_id"),
            row.getInt("attempts"),
            row.getString("last_error"),
            row.getTimestamp("received_at").toInstant());
   }

   private static InboxMessage toMessage(ResultSet row, int rowNumber) throws SQLException {
      return new InboxMessage(row.getObject("id", UUID.class),
            row.getString("source_code"),
            row.getString("external_id"),
            AggregateType.valueOf(row.getString("aggregate_type")),
            row.getString("aggregate_id"),
            row.getString("payload"),
            row.getInt("attempts"));
   }
}
