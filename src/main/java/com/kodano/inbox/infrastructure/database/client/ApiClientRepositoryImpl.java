package com.kodano.inbox.infrastructure.database.client;

import com.kodano.inbox.domain.client.ApiClient;
import com.kodano.inbox.domain.client.ApiClientRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ApiClientRepositoryImpl implements ApiClientRepository {

   private final ApiClientJpaRepository jpaRepository;
   private final ApiClientMapper apiClientMapper;

   @Override
   public Optional<ApiClient> findActiveByTokenHash(String tokenHash) {
      return jpaRepository.findByTokenHashAndRevokedAtIsNull(tokenHash).map(apiClientMapper::toDomain);
   }
}
