package com.kodano.inbox.domain.client;

import java.util.Optional;

public interface ApiClientRepository {

   Optional<ApiClient> findActiveByTokenHash(String tokenHash);
}
