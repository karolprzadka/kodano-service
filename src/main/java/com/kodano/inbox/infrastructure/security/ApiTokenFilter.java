package com.kodano.inbox.infrastructure.security;

import com.kodano.inbox.api.error.ErrorCode;
import com.kodano.inbox.api.error.ProblemWriter;
import com.kodano.inbox.domain.client.ApiClient;
import com.kodano.inbox.domain.client.ApiClientRepository;
import io.vavr.control.Option;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class ApiTokenFilter extends OncePerRequestFilter {

   public static final String SOURCE_ATTRIBUTE = "kodano.source";

   private static final Pattern INGEST_PATH = Pattern.compile("^/api/v1/inbox/(magento|vending)/.+");
   private static final String TOKEN_HEADER = "X-Api-Token";
   private static final String UNKNOWN_TOKEN = "Missing or unknown api token";
   private static final String SCOPE_VIOLATION = "Token is bound to source '%s' and cannot publish as '%s'";

   private final ApiClientRepository apiClientRepository;
   private final ProblemWriter problemWriter;

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
         throws ServletException, IOException {
      Matcher ingest = INGEST_PATH.matcher(request.getRequestURI());
      if (!ingest.matches()) {
         chain.doFilter(request, response);
         return;
      }

      Option<ApiClient> client = Option.of(request.getHeader(TOKEN_HEADER))
            .map(TokenHasher::sha256)
            .flatMap(hash -> Option.ofOptional(apiClientRepository.findActiveByTokenHash(hash)));
      if (client.isEmpty()) {
         problemWriter.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNKNOWN_SOURCE, UNKNOWN_TOKEN);
         return;
      }

      String requestedSource = ingest.group(1);
      String grantedSource = client.get().sourceCode();
      if (!grantedSource.equals(requestedSource)) {
         problemWriter.write(response, HttpStatus.FORBIDDEN, ErrorCode.SOURCE_SCOPE_VIOLATION,
               SCOPE_VIOLATION.formatted(grantedSource, requestedSource));
         return;
      }

      request.setAttribute(SOURCE_ATTRIBUTE, grantedSource);
      chain.doFilter(request, response);
   }
}
