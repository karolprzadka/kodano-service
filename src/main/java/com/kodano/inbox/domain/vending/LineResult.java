package com.kodano.inbox.domain.vending;

import com.kodano.inbox.domain.inbox.IngestStatus;

public record LineResult(Long seq, IngestStatus status, String reason) {

   static LineResult of(Long seq, IngestStatus status) {
      return new LineResult(seq, status, null);
   }

   static LineResult rejected(Long seq, String reason) {
      return new LineResult(seq, IngestStatus.REJECTED, reason);
   }
}
