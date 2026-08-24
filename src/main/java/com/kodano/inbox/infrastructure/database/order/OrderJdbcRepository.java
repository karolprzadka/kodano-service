package com.kodano.inbox.infrastructure.database.order;

import com.kodano.inbox.domain.order.EventEffect;
import com.kodano.inbox.domain.order.OrderEvent;
import com.kodano.inbox.domain.order.OrderEventType;
import com.kodano.inbox.domain.order.OrderProjection;
import com.kodano.inbox.domain.order.OrderRepository;
import com.kodano.inbox.domain.order.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
class OrderJdbcRepository implements OrderRepository {

   private static final String FIND_EVENTS = """
         select event_id, order_id, event_type, occurred_at
         from order_event_log
         where order_id = :orderId
         """;

   private static final String APPEND_EVENT = """
         insert into order_event_log (id, order_id, event_id, event_type, occurred_at, effect)
         values (:id, :orderId, :eventId, :eventType, :occurredAt, :effect)
         on conflict (event_id) do nothing
         """;

   private static final String SAVE_PROJECTION = """
         insert into orders_projection (order_id, status, placed_at, paid_at, cancelled_at, refunded_at, last_event_at)
         values (:orderId, :status, :placedAt, :paidAt, :cancelledAt, :refundedAt, :lastEventAt)
         on conflict (order_id) do update
         set status = excluded.status,
             placed_at = excluded.placed_at,
             paid_at = excluded.paid_at,
             cancelled_at = excluded.cancelled_at,
             refunded_at = excluded.refunded_at,
             last_event_at = excluded.last_event_at,
             updated_at = now()
         """;

   private static final String FIND_PROJECTION = """
         select order_id, status, placed_at, paid_at, cancelled_at, refunded_at, last_event_at
         from orders_projection
         where order_id = :orderId
         """;

   private final NamedParameterJdbcTemplate jdbcTemplate;

   @Override
   public List<OrderEvent> findEvents(String orderId) {
      return jdbcTemplate.query(FIND_EVENTS, new MapSqlParameterSource("orderId", orderId), OrderJdbcRepository::toEvent);
   }

   @Override
   public void appendEvent(OrderEvent event, EventEffect effect) {
      jdbcTemplate.update(APPEND_EVENT, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("orderId", event.orderId())
            .addValue("eventId", event.eventId())
            .addValue("eventType", event.type().name())
            .addValue("occurredAt", Timestamp.from(event.occurredAt()))
            .addValue("effect", effect.name().toLowerCase()));
   }

   @Override
   public void saveProjection(OrderProjection projection) {
      jdbcTemplate.update(SAVE_PROJECTION, new MapSqlParameterSource()
            .addValue("orderId", projection.orderId())
            .addValue("status", projection.status().name())
            .addValue("placedAt", timestamp(projection.placedAt()))
            .addValue("paidAt", timestamp(projection.paidAt()))
            .addValue("cancelledAt", timestamp(projection.cancelledAt()))
            .addValue("refundedAt", timestamp(projection.refundedAt()))
            .addValue("lastEventAt", timestamp(projection.lastEventAt())));
   }

   @Override
   public Optional<OrderProjection> findProjection(String orderId) {
      return jdbcTemplate.query(FIND_PROJECTION, new MapSqlParameterSource("orderId", orderId),
            OrderJdbcRepository::toProjection).stream().findFirst();
   }

   private static OrderEvent toEvent(ResultSet row, int rowNumber) throws SQLException {
      return new OrderEvent(row.getString("event_id"), row.getString("order_id"),
            OrderEventType.valueOf(row.getString("event_type")), row.getTimestamp("occurred_at").toInstant());
   }

   private static OrderProjection toProjection(ResultSet row, int rowNumber) throws SQLException {
      return new OrderProjection(row.getString("order_id"), OrderStatus.valueOf(row.getString("status")),
            instant(row, "placed_at"), instant(row, "paid_at"), instant(row, "cancelled_at"),
            instant(row, "refunded_at"), instant(row, "last_event_at"));
   }

   private static Instant instant(ResultSet row, String column) throws SQLException {
      return Optional.ofNullable(row.getTimestamp(column)).map(Timestamp::toInstant).orElse(null);
   }

   private static Timestamp timestamp(Instant instant) {
      return Optional.ofNullable(instant).map(Timestamp::from).orElse(null);
   }
}
