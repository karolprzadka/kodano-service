package com.kodano.inbox.api.error;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

   private static final String UNREADABLE_BODY = "Request body could not be parsed";

   @ExceptionHandler(MethodArgumentNotValidException.class)
   ProblemDetail onInvalidPayload(MethodArgumentNotValidException exception) {
      String detail = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
      return Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_EVENT, detail);
   }

   @ExceptionHandler(HttpMessageNotReadableException.class)
   ProblemDetail onUnreadablePayload(HttpMessageNotReadableException exception) {
      return Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_EVENT, UNREADABLE_BODY);
   }
}
