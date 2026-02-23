package com.finance.api.repository;

import com.finance.api.entity.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, Long> {
    Optional<BudgetCategory> findByName(String name);
    boolean existsByName(String name);
}
