package com.finance.api.service;

import com.finance.api.dto.AccountDto;
import com.finance.api.dto.TransactionDto;
import com.finance.api.entity.Account;
import com.finance.api.entity.Transaction;
import com.finance.api.exception.BadRequestException;
import com.finance.api.exception.ResourceNotFoundException;
import com.finance.api.repository.AccountRepository;
import com.finance.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountDto.Response createAccount(AccountDto.CreateRequest request) {
        if (accountRepository.existsByName(request.getName())) {
            throw new BadRequestException("Account with name '" + request.getName() + "' already exists");
        }
        Account account = new Account();
        account.setName(request.getName());
        account.setCurrentBalance(request.getInitialBalance());
        Account saved = accountRepository.save(account);
        return toResponse(saved);
    }

    public AccountDto.SummaryResponse getAccountSummary(Long id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));

        List<Transaction> recent = transactionRepository.findTop5ByAccountIdOrderByTransactionDateDesc(id);

        AccountDto.SummaryResponse summary = new AccountDto.SummaryResponse();
        summary.setId(account.getId());
        summary.setName(account.getName());
        summary.setCurrentBalance(account.getCurrentBalance());
        summary.setRecentTransactions(recent.stream().map(this::toTransactionResponse).collect(Collectors.toList()));
        return summary;
    }

    private AccountDto.Response toResponse(Account account) {
        AccountDto.Response res = new AccountDto.Response();
        res.setId(account.getId());
        res.setName(account.getName());
        res.setCurrentBalance(account.getCurrentBalance());
        return res;
    }

    private TransactionDto.Response toTransactionResponse(Transaction tx) {
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
