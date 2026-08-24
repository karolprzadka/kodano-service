package com.kodano.inbox.api.order;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static org.mapstruct.ReportingPolicy.IGNORE;

import com.kodano.inbox.api.OrderDto;
import com.kodano.inbox.domain.order.OrderProjection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.mapstruct.Mapper;

@Mapper(componentModel = SPRING, unmappedTargetPolicy = IGNORE)
interface OrderDtoMapper {

   OrderDto toDto(OrderProjection projection);

   default OffsetDateTime toOffsetDateTime(Instant instant) {
      return Optional.ofNullable(instant).map(value -> value.atOffset(ZoneOffset.UTC)).orElse(null);
   }
}
