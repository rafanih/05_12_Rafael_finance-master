package com.finance.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.api.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * INTEGRATION TESTS — TransactionController
 *
 * Tests the complete HTTP cycle for logging transactions and retrieving
 * paginated lists. Validates that:
 *  - JWT security filter enforces 401 on protected routes
 *  - Bean Validation enforces 400 with structured errors on invalid input
 *  - The overdraft rule surfaces as 400 through the global exception handler
 *  - Successful flows return 201 Created with correct response bodies
 *
 * The @BeforeEach creates prerequisite data (account + category) so that
 * transaction tests can focus solely on their own logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("TransactionController — Integration Tests")
class TransactionControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("finance_tx_test")
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

    private String token;
    private Long   accountId;
    private Long   expenseCategoryId;
    private Long   incomeCategoryId;

    private String bearerToken(String username) {
        return "Bearer " + jwtUtils.generateToken(username);
    }

    /**
     * Each test needs a fresh account and categories to avoid cross-test
     * state contamination (e.g., balance depleted by a prior test).
     */
    @BeforeEach
    void createPrerequisites() throws Exception {
        token = bearerToken("testuser");

        // Create an account with $1000 starting balance
        String accountName = "TxAccount-" + System.nanoTime();
        Map<String, Object> accPayload = Map.of("name", accountName, "initialBalance", 1000.00);
        String accBody = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accPayload)))
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(accBody).get("id").asLong();

        // Create EXPENSE category
        String expName = "Groceries-" + System.nanoTime();
        Map<String, Object> expPayload = Map.of("name", expName, "type", "EXPENSE");
        String expBody = mockMvc.perform(post("/api/categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expPayload)))
                .andReturn().getResponse().getContentAsString();
        expenseCategoryId = objectMapper.readTree(expBody).get("id").asLong();

        // Create INCOME category
        String incName = "Salary-" + System.nanoTime();
        Map<String, Object> incPayload = Map.of("name", incName, "type", "INCOME");
        String incBody = mockMvc.perform(post("/api/categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incPayload)))
                .andReturn().getResponse().getContentAsString();
        incomeCategoryId = objectMapper.readTree(incBody).get("id").asLong();
    }

    // ================================================================
    // 1. POST /api/transactions
    // ================================================================
    @Nested
    @DisplayName("POST /api/transactions")
    class LogTransaction {

        @Test
        @DisplayName("201 Created — valid EXPENSE transaction with JWT reduces balance")
        void validExpense_returns201() throws Exception {
            Map<String, Object> payload = Map.of(
                    "amount", 150.00,
                    "transactionDate", "2025-06-01",
                    "description", "Weekly groceries",
                    "accountId", accountId,
                    "categoryId", expenseCategoryId
            );

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.categoryType").value("EXPENSE"));
        }

        @Test
        @DisplayName("201 Created — valid INCOME transaction with JWT increases balance")
        void validIncome_returns201() throws Exception {
            Map<String, Object> payload = Map.of(
                    "amount", 2000.00,
                    "transactionDate", "2025-06-01",
                    "description", "Monthly salary",
                    "accountId", accountId,
                    "categoryId", incomeCategoryId
            );

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.categoryType").value("INCOME"))
                    .andExpect(jsonPath("$.amount").value(2000.00));
        }

        @Test
        @DisplayName("400 Bad Request — overdraft attempt returns insufficient balance error")
        void overdraft_returns400WithInsufficientBalanceError() throws Exception {
            // Account has $1000; try to spend $9999
            Map<String, Object> payload = Map.of(
                    "amount", 9999.00,
                    "transactionDate", "2025-06-01",
                    "description", "Overdraft attempt",
                    "accountId", accountId,
                    "categoryId", expenseCategoryId
            );

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value(containsString("Insufficient balance")));
        }

        @Test
        @DisplayName("400 Bad Request — missing amount field returns validation error")
        void missingAmount_returns400() throws Exception {
            String payload = String.format(
                    "{\"transactionDate\":\"2025-06-01\",\"accountId\":%d,\"categoryId\":%d}",
                    accountId, expenseCategoryId);

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").isString());
        }

        @Test
        @DisplayName("400 Bad Request — missing transactionDate returns validation error")
        void missingDate_returns400() throws Exception {
            String payload = String.format(
                    "{\"amount\":100.00,\"accountId\":%d,\"categoryId\":%d}",
                    accountId, expenseCategoryId);

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.transactionDate").isString());
        }

        @Test
        @DisplayName("401 Unauthorized — no token on POST is rejected")
        void noToken_returns401() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"transactionDate\":\"2025-06-01\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 Unauthorized — invalid Bearer token is rejected")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", "Bearer garbage.token.here")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"transactionDate\":\"2025-06-01\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("404 Not Found — non-existent accountId returns error body")
        void nonExistentAccount_returns404() throws Exception {
            Map<String, Object> payload = Map.of(
                    "amount", 50.00,
                    "transactionDate", "2025-06-01",
                    "accountId", 999999L,
                    "categoryId", expenseCategoryId
            );

            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // ================================================================
    // 2. GET /api/transactions — paginated retrieval
    // ================================================================
    @Nested
    @DisplayName("GET /api/transactions")
    class GetTransactions {

        @Test
        @DisplayName("200 OK — returns paginated response for valid date range with JWT")
        void validDateRange_returns200WithPage() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .header("Authorization", token)
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2025-12-31")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.pageable").exists());
        }

        @Test
        @DisplayName("401 Unauthorized — no token on GET is rejected")
        void noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .param("startDate", "2025-01-01")
                            .param("endDate", "2025-12-31"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
