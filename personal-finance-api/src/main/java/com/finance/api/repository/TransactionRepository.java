package com.finance.api.repository;

import com.finance.api.entity.CategoryType;
import com.finance.api.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Transaction> findTop5ByAccountIdOrderByTransactionDateDesc(Long accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.category.type = :type " +
           "AND FUNCTION('MONTH', t.transactionDate) = :month " +
           "AND FUNCTION('YEAR', t.transactionDate) = :year")
    BigDecimal sumByTypeAndMonthAndYear(
        @Param("type") CategoryType type,
        @Param("month") int month,
        @Param("year") int year
    );
}
