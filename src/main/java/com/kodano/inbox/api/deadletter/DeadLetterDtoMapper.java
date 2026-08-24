package com.kodano.inbox.api.deadletter;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static org.mapstruct.ReportingPolicy.IGNORE;

import com.kodano.inbox.api.DeadLetterDto;
import com.kodano.inbox.domain.inbox.DeadLetter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;

@Mapper(componentModel = SPRING, unmappedTargetPolicy = IGNORE)
interface DeadLetterDtoMapper {

   List<DeadLetterDto> toDto(List<DeadLetter> deadLetters);

   default OffsetDateTime toOffsetDateTime(Instant instant) {
      return Optional.ofNullable(instant).map(value -> value.atOffset(ZoneOffset.UTC)).orElse(null);
   }
}
