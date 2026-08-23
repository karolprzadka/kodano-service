package com.kodano.inbox.infrastructure.database.inbox;

import com.kodano.inbox.domain.inbox.InboxRepository;
import com.kodano.inbox.domain.inbox.InboxSubmission;
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
}
