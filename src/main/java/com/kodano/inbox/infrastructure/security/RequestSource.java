package com.kodano.inbox.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class RequestSource {

   public String current() {
      return (String) RequestContextHolder.currentRequestAttributes()
            .getAttribute(ApiTokenFilter.SOURCE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
   }
}
