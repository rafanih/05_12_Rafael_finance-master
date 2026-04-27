package com.finance.api.service;

import com.finance.api.dto.TransactionDto;
import com.finance.api.entity.Account;
import com.finance.api.entity.BudgetCategory;
import com.finance.api.entity.CategoryType;
import com.finance.api.entity.Transaction;
import com.finance.api.exception.BadRequestException;
import com.finance.api.exception.ResourceNotFoundException;
import com.finance.api.repository.AccountRepository;
import com.finance.api.repository.BudgetCategoryRepository;
import com.finance.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TESTS — TransactionService
 *
 * Scope  : Pure business logic in isolation.
 * Context: NO Spring context loaded. NO database.
 * Mocking: All repository dependencies mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService — Unit Tests")
class TransactionServiceTest {

    // ---------------------------------------------------------------
    // Mocked dependencies — no real DB, no Spring beans
    // ---------------------------------------------------------------
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository     accountRepository;
    @Mock private BudgetCategoryRepository categoryRepository;

    @InjectMocks
    private TransactionService transactionService;

    // ---------------------------------------------------------------
    // Shared test fixtures
    // ---------------------------------------------------------------
    private Account         account;
    private BudgetCategory  expenseCategory;
    private BudgetCategory  incomeCategory;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);
        account.setName("Checking");
        account.setCurrentBalance(new BigDecimal("500.00"));

        expenseCategory = new BudgetCategory();
        expenseCategory.setId(10L);
        expenseCategory.setName("Groceries");
        expenseCategory.setType(CategoryType.EXPENSE);

        incomeCategory = new BudgetCategory();
        incomeCategory.setId(20L);
        incomeCategory.setName("Salary");
        incomeCategory.setType(CategoryType.INCOME);
    }

    // ---------------------------------------------------------------
    // Helper: build a minimal CreateRequest
    // ---------------------------------------------------------------
    private TransactionDto.CreateRequest buildRequest(BigDecimal amount, Long categoryId) {
        TransactionDto.CreateRequest req = new TransactionDto.CreateRequest();
        req.setAmount(amount);
        req.setTransactionDate(LocalDate.now());
        req.setDescription("Test transaction");
        req.setAccountId(account.getId());
        req.setCategoryId(categoryId);
        return req;
    }

    // ---------------------------------------------------------------
    // Stub helpers
    // ---------------------------------------------------------------
    private void stubSavedTransaction(Account acc, BudgetCategory cat, BigDecimal amount) {
        Transaction saved = new Transaction();
        saved.setId(99L);
        saved.setAmount(amount);
        saved.setTransactionDate(LocalDate.now());
        saved.setDescription("Test transaction");
        saved.setAccount(acc);
        saved.setCategory(cat);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
    }

    // ===============================================================
    // 1. OVERDRAFT PROTECTION
    // ===============================================================
    @Nested
    @DisplayName("Overdraft Protection")
    class OverdraftProtection {

        @Test
        @DisplayName("throws BadRequestException when EXPENSE amount exceeds account balance")
        void expense_exceedsBalance_throwsBadRequestException() {
            // ARRANGE
            BigDecimal overdraftAmount = new BigDecimal("600.00"); // balance is 500.00
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory));

            TransactionDto.CreateRequest request = buildRequest(overdraftAmount, 10L);

            // ACT + ASSERT
            assertThatThrownBy(() -> transactionService.logTransaction(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient balance");

            // Verify the account was never persisted in an overdraft state
            verify(accountRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("does NOT throw when EXPENSE amount exactly equals balance (boundary)")
        void expense_exactlyEqualsBalance_succeeds() {
            BigDecimal exactBalance = new BigDecimal("500.00");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory));
            stubSavedTransaction(account, expenseCategory, exactBalance);

            TransactionDto.CreateRequest request = buildRequest(exactBalance, 10L);

            // Should complete without throwing
            transactionService.logTransaction(request);

            // Account balance should be 0 after the transaction
            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrentBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("INCOME transaction is never blocked by overdraft guard")
        void income_neverBlockedByOverdraftGuard() {
            BigDecimal largeIncome = new BigDecimal("999999.99");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(20L)).thenReturn(Optional.of(incomeCategory));
            stubSavedTransaction(account, incomeCategory, largeIncome);

            TransactionDto.CreateRequest request = buildRequest(largeIncome, 20L);

            // Must not throw — income has no overdraft check
            transactionService.logTransaction(request);
        }
    }

    // ===============================================================
    // 2. BALANCE MUTATION — INCOME adds, EXPENSE subtracts
    // ===============================================================
    @Nested
    @DisplayName("Balance Mutation")
    class BalanceMutation {

        @Test
        @DisplayName("INCOME category correctly ADDS amount to account balance")
        void income_addsToBalance() {
            BigDecimal income = new BigDecimal("200.00");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(20L)).thenReturn(Optional.of(incomeCategory));
            stubSavedTransaction(account, incomeCategory, income);

            transactionService.logTransaction(buildRequest(income, 20L));

            // Capture the account passed to save() and check balance
            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());

            assertThat(captor.getValue().getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("700.00")); // 500 + 200
        }

        @Test
        @DisplayName("EXPENSE category correctly SUBTRACTS amount from account balance")
        void expense_subtractsFromBalance() {
            BigDecimal expense = new BigDecimal("150.00");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(expenseCategory));
            stubSavedTransaction(account, expenseCategory, expense);

            transactionService.logTransaction(buildRequest(expense, 10L));

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());

            assertThat(captor.getValue().getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("350.00")); // 500 - 150
        }

        @Test
        @DisplayName("response DTO reflects the correct category type after INCOME transaction")
        void income_responseDtoHasCorrectCategoryType() {
            BigDecimal income = new BigDecimal("100.00");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(20L)).thenReturn(Optional.of(incomeCategory));
            stubSavedTransaction(account, incomeCategory, income);

            TransactionDto.Response response =
                    transactionService.logTransaction(buildRequest(income, 20L));

            assertThat(response.getCategoryType()).isEqualTo(CategoryType.INCOME);
            assertThat(response.getCategoryName()).isEqualTo("Salary");
        }
    }

    // ===============================================================
    // 3. INPUT VALIDATION GUARD
    // ===============================================================
    @Nested
    @DisplayName("Input Validation Guard")
    class InputValidationGuard {

        @Test
        @DisplayName("throws BadRequestException when amount is zero")
        void amount_zero_throwsBadRequestException() {
            TransactionDto.CreateRequest req = buildRequest(BigDecimal.ZERO, 10L);

            assertThatThrownBy(() -> transactionService.logTransaction(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Amount must be greater than 0");

            verifyNoInteractions(accountRepository, categoryRepository, transactionRepository);
        }

        @Test
        @DisplayName("throws BadRequestException when amount is negative")
        void amount_negative_throwsBadRequestException() {
            TransactionDto.CreateRequest req = buildRequest(new BigDecimal("-10.00"), 10L);

            assertThatThrownBy(() -> transactionService.logTransaction(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when account ID does not exist")
        void accountNotFound_throwsResourceNotFoundException() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());
            TransactionDto.CreateRequest req = buildRequest(new BigDecimal("10.00"), 10L);
            req.setAccountId(999L);

            assertThatThrownBy(() -> transactionService.logTransaction(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when category ID does not exist")
        void categoryNotFound_throwsResourceNotFoundException() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());
            TransactionDto.CreateRequest req = buildRequest(new BigDecimal("10.00"), 999L);

            assertThatThrownBy(() -> transactionService.logTransaction(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found");
        }
    }
}
