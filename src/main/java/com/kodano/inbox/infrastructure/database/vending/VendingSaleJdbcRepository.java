package com.kodano.inbox.infrastructure.database.vending;

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

   private static final String COUNT_SALES = """
         select count(*)
         from vending_sales
         where device_id = :deviceId
           and (cast(:day as date) is null or (sold_at at time zone 'UTC')::date = cast(:day as date))
         """;

   private static final String FIND_MISSING_SEQS = """
         select missing.seq
         from generate_series(1, coalesce((select max(seq) from vending_sales where device_id = :deviceId), 0)) as missing(seq)
         where not exists (select 1 from vending_sales where device_id = :deviceId and seq = missing.seq)
         order by missing.seq
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
   public long countSales(String deviceId, LocalDate day) {
      return jdbcTemplate.queryForObject(COUNT_SALES, new MapSqlParameterSource()
            .addValue("deviceId", deviceId)
            .addValue("day", day), Long.class);
   }

   @Override
   public List<Long> findMissingSeqs(String deviceId) {
      return jdbcTemplate.queryForList(FIND_MISSING_SEQS, new MapSqlParameterSource("deviceId", deviceId), Long.class);
   }
}
