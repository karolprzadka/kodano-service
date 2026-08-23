package com.kodano.inbox.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProblemWriter {

   private final ObjectMapper objectMapper;

   public void write(HttpServletResponse response, HttpStatus status, ErrorCode code, String detail) throws IOException {
      response.setStatus(status.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), Problems.of(status, code, detail));
   }
}
