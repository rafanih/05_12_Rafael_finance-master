package com.finance.api.service;

import com.finance.api.dto.CategoryDto;
import com.finance.api.entity.BudgetCategory;
import com.finance.api.entity.CategoryType;
import com.finance.api.exception.BadRequestException;
import com.finance.api.repository.BudgetCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TESTS — CategoryService
 *
 * Scope  : Category creation, duplicate detection, type persistence.
 * Context: NO Spring context. NO database. Pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService — Unit Tests")
class CategoryServiceTest {

    @Mock private BudgetCategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    private CategoryDto.CreateRequest buildRequest(String name, CategoryType type) {
        CategoryDto.CreateRequest req = new CategoryDto.CreateRequest();
        req.setName(name);
        req.setType(type);
        return req;
    }

    private BudgetCategory buildSaved(Long id, String name, CategoryType type) {
        BudgetCategory cat = new BudgetCategory();
        cat.setId(id);
        cat.setName(name);
        cat.setType(type);
        return cat;
    }

    // ===============================================================
    // 1. CATEGORY CREATION
    // ===============================================================
    @Nested
    @DisplayName("createCategory()")
    class CreateCategory {

        @Test
        @DisplayName("INCOME category is persisted and returned in response DTO")
        void incomeCategory_persistedCorrectly() {
            when(categoryRepository.existsByName("Salary")).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(buildSaved(1L, "Salary", CategoryType.INCOME));

            CategoryDto.Response response = categoryService.createCategory(buildRequest("Salary", CategoryType.INCOME));

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Salary");
            assertThat(response.getType()).isEqualTo(CategoryType.INCOME);
        }

        @Test
        @DisplayName("EXPENSE category is persisted and returned in response DTO")
        void expenseCategory_persistedCorrectly() {
            when(categoryRepository.existsByName("Rent")).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(buildSaved(2L, "Rent", CategoryType.EXPENSE));

            CategoryDto.Response response = categoryService.createCategory(buildRequest("Rent", CategoryType.EXPENSE));

            assertThat(response.getType()).isEqualTo(CategoryType.EXPENSE);
        }

        @Test
        @DisplayName("throws BadRequestException on duplicate category name")
        void duplicateName_throwsBadRequestException() {
            when(categoryRepository.existsByName("Salary")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.createCategory(buildRequest("Salary", CategoryType.INCOME)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("category type is preserved exactly — INCOME stays INCOME")
        void categoryType_preservedAsIncome() {
            when(categoryRepository.existsByName("Freelance")).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(buildSaved(3L, "Freelance", CategoryType.INCOME));

            CategoryDto.Response res = categoryService.createCategory(buildRequest("Freelance", CategoryType.INCOME));

            assertThat(res.getType()).isNotEqualTo(CategoryType.EXPENSE);
        }

        @Test
        @DisplayName("repository.save() is called exactly once on valid request")
        void validRequest_saveCalledExactlyOnce() {
            when(categoryRepository.existsByName("Utilities")).thenReturn(false);
            when(categoryRepository.save(any())).thenReturn(buildSaved(4L, "Utilities", CategoryType.EXPENSE));

            categoryService.createCategory(buildRequest("Utilities", CategoryType.EXPENSE));

            verify(categoryRepository, times(1)).save(any());
        }
    }
}
