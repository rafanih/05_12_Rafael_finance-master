package com.finance.api.dto;

import com.finance.api.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CategoryDto {

    public static class CreateRequest {
        @NotBlank(message = "Category name is required")
        private String name;

        @NotNull(message = "Category type is required")
        private CategoryType type;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public CategoryType getType() { return type; }
        public void setType(CategoryType type) { this.type = type; }
    }

    public static class Response {
        private Long id;
        private String name;
        private CategoryType type;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public CategoryType getType() { return type; }
        public void setType(CategoryType type) { this.type = type; }
    }
}
