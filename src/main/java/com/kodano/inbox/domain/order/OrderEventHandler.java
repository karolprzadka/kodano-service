package com.kodano.inbox.domain.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodano.inbox.domain.inbox.AggregateType;
import com.kodano.inbox.domain.inbox.InboxMessage;
import com.kodano.inbox.domain.inbox.MessageHandler;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class OrderEventHandler implements MessageHandler {

   private final ObjectMapper objectMapper;
   private final OrderRepository orderRepository;

   @Override
   public AggregateType aggregateType() {
      return AggregateType.ORDER;
   }

   @Override
   public void handle(InboxMessage message) {
      OrderEvent event = toEvent(message);
      List<OrderEvent> history = Stream.concat(orderRepository.findEvents(event.orderId()).stream(), Stream.of(event)).toList();
      OrderFold fold = OrderProjectionFold.fold(event.orderId(), history);

      orderRepository.appendEvent(event, fold.effectOf(event.eventId()));
      orderRepository.saveProjection(fold.projection());
   }

   private OrderEvent toEvent(InboxMessage message) {
      RawEvent raw = Try.of(() -> objectMapper.readValue(message.payload(), RawEvent.class)).get();
      return new OrderEvent(raw.eventId(), raw.orderId(), OrderEventType.fromWireName(raw.type()), raw.occurredAt());
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   private record RawEvent(String eventId, String orderId, String type, Instant occurredAt) {
   }
}
