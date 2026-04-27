package com.finance.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.api.security.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * INTEGRATION TESTS — CategoryController
 *
 * Uses a dedicated Testcontainers PostgreSQL instance (shared within this class).
 * Validates HTTP lifecycle: JSON validation → 400, JWT auth → 201/401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("CategoryController — Integration Tests")
class CategoryControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("finance_cat_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtils     jwtUtils;

    private String bearerToken(String username) {
        return "Bearer " + jwtUtils.generateToken(username);
    }

    // ================================================================
    // POST /api/categories
    // ================================================================
    @Nested
    @DisplayName("POST /api/categories")
    class CreateCategory {

        @Test
        @DisplayName("201 Created — INCOME category with valid JWT")
        void incomeCategory_returns201() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "Salary-" + System.currentTimeMillis(),
                    "type", "INCOME"
            );

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.type").value("INCOME"));
        }

        @Test
        @DisplayName("201 Created — EXPENSE category with valid JWT")
        void expenseCategory_returns201() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "Utilities-" + System.currentTimeMillis(),
                    "type", "EXPENSE"
            );

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("EXPENSE"));
        }

        @Test
        @DisplayName("400 Bad Request — blank name returns field-level validation error")
        void blankName_returns400WithFieldError() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "",
                    "type", "EXPENSE"
            );

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors.name").isString());
        }

        @Test
        @DisplayName("400 Bad Request — missing type field returns validation error")
        void missingType_returns400() throws Exception {
            String payload = "{\"name\":\"No Type Category\"}";

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").isString());
        }

        @Test
        @DisplayName("400 Bad Request — duplicate name returns error body with message")
        void duplicateName_returns400() throws Exception {
            String name = "Duplicate-Cat-" + System.currentTimeMillis();
            Map<String, Object> payload = Map.of("name", name, "type", "INCOME");
            String token = bearerToken("alice");

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/categories")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("already exists")));
        }

        @Test
        @DisplayName("401 Unauthorized — no token is rejected by security filter")
        void noToken_returns401() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Ghost\",\"type\":\"INCOME\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 Unauthorized — expired / invalid token is rejected")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake.token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Ghost\",\"type\":\"INCOME\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
