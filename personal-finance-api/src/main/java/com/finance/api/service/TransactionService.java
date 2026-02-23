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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetCategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              BudgetCategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public TransactionDto.Response logTransaction(TransactionDto.CreateRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }

        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + request.getAccountId()));

        BudgetCategory category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        if (category.getType() == CategoryType.EXPENSE) {
            if (request.getAmount().compareTo(account.getCurrentBalance()) > 0) {
                throw new BadRequestException("Insufficient balance. Current balance: " + account.getCurrentBalance());
            }
            account.setCurrentBalance(account.getCurrentBalance().subtract(request.getAmount()));
        } else {
            account.setCurrentBalance(account.getCurrentBalance().add(request.getAmount()));
        }
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAmount(request.getAmount());
        tx.setTransactionDate(request.getTransactionDate());
        tx.setDescription(request.getDescription());
        tx.setAccount(account);
        tx.setCategory(category);
        Transaction saved = transactionRepository.save(tx);

        return toResponse(saved);
    }

    public Page<TransactionDto.Response> getTransactions(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return transactionRepository.findByTransactionDateBetween(startDate, endDate, pageable)
            .map(this::toResponse);
    }

    private TransactionDto.Response toResponse(Transaction tx) {
        TransactionDto.Response res = new TransactionDto.Response();
        res.setId(tx.getId());
        res.setAmount(tx.getAmount());
        res.setTransactionDate(tx.getTransactionDate());
        res.setDescription(tx.getDescription());
        res.setAccountId(tx.getAccount().getId());
        res.setAccountName(tx.getAccount().getName());
        res.setCategoryId(tx.getCategory().getId());
        res.setCategoryName(tx.getCategory().getName());
        res.setCategoryType(tx.getCategory().getType());
        return res;
    }
}
