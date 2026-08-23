package com.kodano.inbox.infrastructure.database.client;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static org.mapstruct.ReportingPolicy.IGNORE;

import com.kodano.inbox.domain.client.ApiClient;
import org.mapstruct.Mapper;

@Mapper(componentModel = SPRING, unmappedTargetPolicy = IGNORE)
interface ApiClientMapper {

   ApiClient toDomain(ApiClientEntity entity);
}
