package com.kodano.inbox.api.order;

import com.kodano.inbox.api.OrderDto;
import com.kodano.inbox.api.OrdersApi;
import com.kodano.inbox.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class OrdersController implements OrdersApi {

   private final OrderRepository orderRepository;
   private final OrderDtoMapper orderDtoMapper;

   @Override
   public ResponseEntity<OrderDto> getOrder(String orderId) {
      return orderRepository.findProjection(orderId)
            .map(orderDtoMapper::toDto)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
   }
}
