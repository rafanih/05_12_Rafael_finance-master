package com.finance.api.service;

import com.finance.api.dto.CategoryDto;
import com.finance.api.entity.BudgetCategory;
import com.finance.api.exception.BadRequestException;
import com.finance.api.repository.BudgetCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final BudgetCategoryRepository categoryRepository;

    public CategoryService(BudgetCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryDto.Response createCategory(CategoryDto.CreateRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new BadRequestException("Category with name '" + request.getName() + "' already exists");
        }
        BudgetCategory category = new BudgetCategory();
        category.setName(request.getName());
        category.setType(request.getType());
        BudgetCategory saved = categoryRepository.save(category);

        CategoryDto.Response res = new CategoryDto.Response();
        res.setId(saved.getId());
        res.setName(saved.getName());
        res.setType(saved.getType());
        return res;
    }
}
