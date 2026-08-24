package com.kodano.inbox.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

   List<OrderEvent> findEvents(String orderId);

   void appendEvent(OrderEvent event, EventEffect effect);

   void saveProjection(OrderProjection projection);

   Optional<OrderProjection> findProjection(String orderId);
}
