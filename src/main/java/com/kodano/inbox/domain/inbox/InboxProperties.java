package com.kodano.inbox.domain.inbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inbox")
public record InboxProperties(int batchSize, int maxAttempts, Duration backoff) {
}
