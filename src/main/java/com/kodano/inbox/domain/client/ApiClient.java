package com.kodano.inbox.domain.client;

import java.util.UUID;

public record ApiClient(UUID id, String name, String sourceCode) {
}
