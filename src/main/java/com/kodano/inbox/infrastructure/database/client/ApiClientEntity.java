package com.kodano.inbox.infrastructure.database.client;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "api_clients")
class ApiClientEntity {

   @Id
   private UUID id;
   private String name;
   private String tokenHash;
   private String sourceCode;
   private Instant revokedAt;
}
