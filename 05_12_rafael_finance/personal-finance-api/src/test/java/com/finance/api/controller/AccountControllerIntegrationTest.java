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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * INTEGRATION TESTS — AccountController
 *
 * Infrastructure:
 *   • @SpringBootTest     — loads the FULL application context
 *   • @AutoConfigureMockMvc — provides MockMvc without a real HTTP server
 *   • Testcontainers      — spins up an isolated PostgreSQL Docker container;
 *                           the container is shared across all tests in this class
 *                           via a static @Container field (reuse = true).
 *   • @ActiveProfiles     — activates application-test.properties overrides
 *
 * Security: every protected endpoint is tested with and without a JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("AccountController — Integration Tests")
class AccountControllerIntegrationTest {

    // ----------------------------------------------------------------
    // Testcontainers — one PostgreSQL container shared for the class
    // ----------------------------------------------------------------
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("finance_test")
                    .withUsername("test")
                    .withPassword("test");

    /**
     * Inject the running container's JDBC coordinates into the Spring
     * Environment before the application context starts. This means the
     * real JPA layer is wired to our ephemeral test database — zero config
     * needed in application.properties.
     */
    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtils    jwtUtils;

    // ----------------------------------------------------------------
    // Token helpers
    // ----------------------------------------------------------------
    private String bearerToken(String username) {
        return "Bearer " + jwtUtils.generateToken(username);
    }

    // ================================================================
    // 1. POST /api/accounts — CREATE ACCOUNT
    // ================================================================
    @Nested
    @DisplayName("POST /api/accounts")
    class CreateAccount {

        @Test
        @DisplayName("201 Created — valid payload with JWT returns the new account")
        void validPayload_returns201() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "Integration Test Savings",
                    "initialBalance", 500.00
            );

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Integration Test Savings"))
                    .andExpect(jsonPath("$.currentBalance").value(500.00));
        }

        @Test
        @DisplayName("400 Bad Request — blank accountName returns structured error JSON")
        void blankName_returns400WithErrorStructure() throws Exception {
            // The required field 'name' is intentionally blank
            Map<String, Object> payload = Map.of(
                    "name", "   ",
                    "initialBalance", 100.00
            );

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    // GlobalExceptionHandler wraps validation errors under "errors"
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors").isMap())
                    .andExpect(jsonPath("$.errors.name").isString());
        }

        @Test
        @DisplayName("400 Bad Request — missing initialBalance returns structured error JSON")
        void missingInitialBalance_returns400() throws Exception {
            // initialBalance is @NotNull — omitting it must fail validation
            String payload = "{\"name\": \"Only Name Account\"}";

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.initialBalance").exists());
        }

        @Test
        @DisplayName("400 Bad Request — negative initialBalance is rejected")
        void negativeInitialBalance_returns400() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "Negative Balance Account",
                    "initialBalance", -50.00
            );

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", bearerToken("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.initialBalance").isString());
        }

        @Test
        @DisplayName("401 Unauthorized — request without Authorization header is rejected")
        void noToken_returns401() throws Exception {
            Map<String, Object> payload = Map.of(
                    "name", "Unauthorized Account",
                    "initialBalance", 100.00
            );

            mockMvc.perform(post("/api/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 Unauthorized — tampered / invalid JWT is rejected")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", "Bearer not.a.real.token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"initialBalance\":100}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("400 Bad Request — duplicate account name returns error body")
        void duplicateName_returns400() throws Exception {
            String uniqueName = "Unique-" + System.currentTimeMillis();
            Map<String, Object> payload = Map.of("name", uniqueName, "initialBalance", 50.00);
            String token = bearerToken("alice");

            // First creation — should succeed
            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());

            // Second creation with same name — must fail with 400
            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("already exists")));
        }
    }

    // ================================================================
    // 2. GET /api/accounts/{id} — ACCOUNT SUMMARY
    // ================================================================
    @Nested
    @DisplayName("GET /api/accounts/{id}")
    class GetAccountSummary {

        @Test
        @DisplayName("200 OK — valid JWT returns account summary for existing account")
        void existingAccount_returns200WithSummary() throws Exception {
            // Create an account first
            Map<String, Object> createPayload = Map.of(
                    "name", "Summary Account " + System.currentTimeMillis(),
                    "initialBalance", 1000.00
            );
            String token = bearerToken("bob");

            String body = mockMvc.perform(post("/api/accounts")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createPayload)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long id = objectMapper.readTree(body).get("id").asLong();

            // Now GET the summary
            mockMvc.perform(get("/api/accounts/{id}", id)
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.currentBalance").value(1000.00))
                    .andExpect(jsonPath("$.recentTransactions").isArray());
        }

        @Test
        @DisplayName("404 Not Found — non-existent account id returns error JSON")
        void nonExistentId_returns404() throws Exception {
            mockMvc.perform(get("/api/accounts/{id}", 999999L)
                            .header("Authorization", bearerToken("bob")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").isString());
        }

        @Test
        @DisplayName("401 Unauthorized — no token on GET is rejected")
        void noToken_returns401OnGet() throws Exception {
            mockMvc.perform(get("/api/accounts/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
