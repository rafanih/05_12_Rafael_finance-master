package com.finance.api.dto;

import com.finance.api.entity.CategoryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDto {

    public static class CreateRequest {
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        private BigDecimal amount;

        @NotNull(message = "Transaction date is required")
        private LocalDate transactionDate;

        private String description;

        @NotNull(message = "Account ID is required")
        private Long accountId;

        @NotNull(message = "Category ID is required")
        private Long categoryId;

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public LocalDate getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    }

    public static class Response {
        private Long id;
        private BigDecimal amount;
        private LocalDate transactionDate;
        private String description;
        private Long accountId;
        private String accountName;
        private Long categoryId;
        private String categoryName;
        private CategoryType categoryType;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public LocalDate getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }

        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public CategoryType getCategoryType() { return categoryType; }
        public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }
    }
}
