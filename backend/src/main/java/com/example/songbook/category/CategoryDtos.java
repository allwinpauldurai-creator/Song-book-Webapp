package com.example.songbook.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryResponse(UUID id, String name, String description) {
        public static CategoryResponse from(BookCategory c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
        }
    }

    public record AdminCategoryResponse(UUID id, String name, String description, boolean active, Long version) {
        public static AdminCategoryResponse from(BookCategory c) {
            return new AdminCategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.getVersion());
        }
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 2000) String description) {
    }

    public record UpdateCategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 2000) String description,
                                        boolean active, @NotNull Long version) {
    }
}
