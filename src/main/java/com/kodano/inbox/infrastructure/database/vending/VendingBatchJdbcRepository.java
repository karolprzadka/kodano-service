package com.kodano.inbox.infrastructure.database.vending;

import com.kodano.inbox.domain.vending.VendingBatchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class VendingBatchJdbcRepository implements VendingBatchRepository {

   private static final String REGISTER_IF_ABSENT = """
         insert into vending_batches (id, device_id, batch_external_id, line_count)
         values (:id, :deviceId, :batchId, :lineCount)
         on conflict (device_id, batch_external_id) do nothing
         """;

   private final NamedParameterJdbcTemplate jdbcTemplate;

   @Override
   public void registerIfAbsent(String deviceId, String batchId, int lineCount) {
      jdbcTemplate.update(REGISTER_IF_ABSENT, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("deviceId", deviceId)
            .addValue("batchId", batchId)
            .addValue("lineCount", lineCount));
   }
}
