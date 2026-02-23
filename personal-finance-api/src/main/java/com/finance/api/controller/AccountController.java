package com.finance.api.controller;

import com.finance.api.dto.AccountDto;
import com.finance.api.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountDto.Response> createAccount(@Valid @RequestBody AccountDto.CreateRequest request) {
        AccountDto.Response response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDto.SummaryResponse> getAccountSummary(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountSummary(id));
    }
}
