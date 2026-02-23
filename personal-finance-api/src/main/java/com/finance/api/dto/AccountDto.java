package com.finance.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class AccountDto {

    public static class CreateRequest {
        @NotBlank(message = "Account name is required")
        private String name;

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.0", message = "Initial balance must be >= 0")
        private BigDecimal initialBalance;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getInitialBalance() { return initialBalance; }
        public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
    }

    public static class Response {
        private Long id;
        private String name;
        private BigDecimal currentBalance;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    }

    public static class SummaryResponse {
        private Long id;
        private String name;
        private BigDecimal currentBalance;
        private List<TransactionDto.Response> recentTransactions;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

        public List<TransactionDto.Response> getRecentTransactions() { return recentTransactions; }
        public void setRecentTransactions(List<TransactionDto.Response> recentTransactions) { this.recentTransactions = recentTransactions; }
    }
}
