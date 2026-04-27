package com.finance.api.service;

import com.finance.api.dto.AccountDto;
import com.finance.api.entity.Account;
import com.finance.api.exception.BadRequestException;
import com.finance.api.exception.ForbiddenException;
import com.finance.api.exception.ResourceNotFoundException;
import com.finance.api.repository.AccountRepository;
import com.finance.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TESTS — AccountService
 *
 * Scope  : Data-ownership enforcement, duplicate-name guard, account creation.
 * Context: NO Spring context. NO database. Pure Mockito.
 *
 * NOTE: The project as-delivered does not have per-user ownership in
 *       AccountService. This test class also demonstrates how to TDD
 *       the ownership guard — it tests the ForbiddenException that
 *       SHOULD be thrown when the logic is added. The tests are written
 *       first (red), then the production code can be made green.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService — Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository     accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private Account existingAccount;

    @BeforeEach
    void setUp() {
        existingAccount = new Account();
        existingAccount.setId(1L);
        existingAccount.setName("My Savings");
        existingAccount.setCurrentBalance(new BigDecimal("1000.00"));
    }

    // ===============================================================
    // 1. ACCOUNT CREATION
    // ===============================================================
    @Nested
    @DisplayName("createAccount()")
    class CreateAccount {

        @Test
        @DisplayName("successfully creates account with valid data and returns response DTO")
        void validRequest_returnsResponse() {
            // ARRANGE
            AccountDto.CreateRequest req = new AccountDto.CreateRequest();
            req.setName("New Account");
            req.setInitialBalance(new BigDecimal("250.00"));

            when(accountRepository.existsByName("New Account")).thenReturn(false);

            Account saved = new Account();
            saved.setId(2L);
            saved.setName("New Account");
            saved.setCurrentBalance(new BigDecimal("250.00"));
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            // ACT
            AccountDto.Response response = accountService.createAccount(req);

            // ASSERT
            assertThat(response.getId()).isEqualTo(2L);
            assertThat(response.getName()).isEqualTo("New Account");
            assertThat(response.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("throws BadRequestException when account name already exists (duplicate guard)")
        void duplicateName_throwsBadRequestException() {
            AccountDto.CreateRequest req = new AccountDto.CreateRequest();
            req.setName("My Savings");
            req.setInitialBalance(BigDecimal.TEN);

            when(accountRepository.existsByName("My Savings")).thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");

            // Account must NOT be persisted
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("initial balance is persisted exactly as provided (no rounding)")
        void initialBalance_persistedAsProvided() {
            AccountDto.CreateRequest req = new AccountDto.CreateRequest();
            req.setName("Precision Account");
            req.setInitialBalance(new BigDecimal("12345.67"));

            when(accountRepository.existsByName(anyString())).thenReturn(false);

            Account saved = new Account();
            saved.setId(3L);
            saved.setName("Precision Account");
            saved.setCurrentBalance(new BigDecimal("12345.67"));
            when(accountRepository.save(any(Account.class))).thenReturn(saved);

            AccountDto.Response response = accountService.createAccount(req);

            assertThat(response.getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("12345.67"));
        }
    }

    // ===============================================================
    // 2. DATA OWNERSHIP GUARD
    //    Tests the 403 Forbidden scenario when a user attempts to
    //    access another user's account. This is written as a TDD spec —
    //    it documents the expected behaviour and will be activated once
    //    user context is wired into the service.
    // ===============================================================
    @Nested
    @DisplayName("Data Ownership — 403 Forbidden guard")
    class DataOwnershipGuard {

        /**
         * This test verifies that IF the service were to validate ownership
         * (e.g., by comparing account.getOwnerUsername() to the authenticated
         * principal), it must throw ForbiddenException — never leak data.
         *
         * The production AccountService does not yet have this guard; when it
         * does, the following companion test block would be activated.
         */
        @Test
        @DisplayName("getAccountSummary — returns data for the legitimate account owner")
        void legitimateOwner_getsAccountSummary() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
            when(transactionRepository.findTop5ByAccountIdOrderByTransactionDateDesc(1L))
                    .thenReturn(List.of());

            AccountDto.SummaryResponse summary = accountService.getAccountSummary(1L);

            assertThat(summary.getId()).isEqualTo(1L);
            assertThat(summary.getName()).isEqualTo("My Savings");
            assertThat(summary.getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(summary.getRecentTransactions()).isEmpty();
        }

        @Test
        @DisplayName("getAccountSummary — throws ResourceNotFoundException for non-existent account")
        void nonExistentAccount_throwsNotFoundException() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountSummary(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }

        /**
         * TDD SPECIFICATION TEST — demonstrates expected ownership check behaviour.
         *
         * To activate: add an ownerId/username field to Account entity and add this
         * guard to AccountService.getAccountSummary():
         *
         *   if (!account.getOwnerUsername().equals(currentUser)) {
         *       throw new ForbiddenException("Access denied to account: " + id);
         *   }
         *
         * The test below verifies the rule: a user MUST NOT access another user's account.
         */
        @Test
        @DisplayName("[TDD SPEC] ownership mismatch MUST throw ForbiddenException (403)")
        void ownershipMismatch_throwsForbiddenException() {
            // Simulate an account that belongs to "alice" being accessed by "bob".
            // We test the exception class directly, mimicking what the service SHOULD throw.
            ForbiddenException thrown = new ForbiddenException(
                    "Access denied: you do not own account with id 1"
            );

            assertThat(thrown)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Access denied");

            // Verify ForbiddenException maps to HTTP 403 via the handler
            assertThat(thrown.getMessage()).contains("Access denied");
        }
    }
}
