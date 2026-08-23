package com.kodano.inbox.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class Problems {

   private static final String CODE_PROPERTY = "code";

   private Problems() {
   }

   public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail) {
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
      problem.setProperty(CODE_PROPERTY, code.name());
      return problem;
   }
}
