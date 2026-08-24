package com.kodano.inbox.infrastructure.database.vending;

import com.kodano.inbox.domain.vending.DeviceTotals;
import com.kodano.inbox.domain.vending.SeqRange;
import com.kodano.inbox.domain.vending.VendingSale;
import com.kodano.inbox.domain.vending.VendingSaleRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class VendingSaleJdbcRepository implements VendingSaleRepository {

   private static final String INSERT_IF_ABSENT = """
         insert into vending_sales (id, device_id, seq, sale_external_id, sku, qty, amount_minor, currency, sold_at)
         values (:id, :deviceId, :seq, :saleId, :sku, :qty, :amountMinor, :currency, :soldAt)
         on conflict (device_id, seq) do nothing
         """;

   private static final String TOTALS = """
         select count(*) as accepted_count, coalesce(sum(amount_minor), 0) as amount_minor_checksum
         from vending_sales
         where device_id = :deviceId
           and (cast(:day as date) is null or (sold_at at time zone 'UTC')::date = cast(:day as date))
         """;

   private static final String FIND_MISSING_RANGES = """
         with received as (
            select seq, lag(seq) over (order by seq) as previous_seq
            from vending_sales
            where device_id = :deviceId
         )
         select coalesce(previous_seq + 1, 1) as range_from, seq - 1 as range_to
         from received
         where (previous_seq is null and seq > 1) or seq - previous_seq > 1
         order by range_from
         """;

   private final NamedParameterJdbcTemplate jdbcTemplate;

   @Override
   public void insertIfAbsent(VendingSale sale) {
      jdbcTemplate.update(INSERT_IF_ABSENT, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("deviceId", sale.deviceId())
            .addValue("seq", sale.seq())
            .addValue("saleId", sale.saleId())
            .addValue("sku", sale.sku())
            .addValue("qty", sale.qty())
            .addValue("amountMinor", sale.amountMinor())
            .addValue("currency", sale.currency())
            .addValue("soldAt", Timestamp.from(sale.soldAt())));
   }

   @Override
   public DeviceTotals totals(String deviceId, LocalDate day) {
      return jdbcTemplate.queryForObject(TOTALS, new MapSqlParameterSource()
            .addValue("deviceId", deviceId)
            .addValue("day", day),
            (row, rowNumber) -> new DeviceTotals(row.getLong("accepted_count"), row.getLong("amount_minor_checksum")));
   }

   @Override
   public List<SeqRange> findMissingRanges(String deviceId) {
      return jdbcTemplate.query(FIND_MISSING_RANGES, new MapSqlParameterSource("deviceId", deviceId),
            (row, rowNumber) -> new SeqRange(row.getLong("range_from"), row.getLong("range_to")));
   }
}
