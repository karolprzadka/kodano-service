package com.kodano.inbox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

   protected static final String MAGENTO_TOKEN = "magento-dev-token";
   protected static final String VENDING_TOKEN = "vending-dev-token";

   private static final String TOKEN_HEADER = "X-Api-Token";
   private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

   static {
      POSTGRES.start();
   }

   @Autowired
   protected MockMvc mockMvc;

   @Autowired
   protected JdbcTemplate jdbcTemplate;

   @DynamicPropertySource
   static void datasourceProperties(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
   }

   protected MvcResult deliver(String token, String path, String body) throws Exception {
      return mockMvc.perform(post(path).header(TOKEN_HEADER, token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn();
   }

   protected static String readString(MvcResult result, String jsonPath) throws Exception {
      return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
   }

   protected static Map<String, Object> readBody(MvcResult result) throws Exception {
      return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
   }
}
