package com.example.songbook.category;

import com.example.songbook.category.CategoryDtos.AdminCategoryResponse;
import com.example.songbook.category.CategoryDtos.CreateCategoryRequest;
import com.example.songbook.category.CategoryDtos.UpdateCategoryRequest;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - categories")
@SecurityRequirement(name = "bearerAuth")
public class AdminCategoryController {

    private final CategoryService service;

    public AdminCategoryController(CategoryService service) {
        this.service = service;
    }

    /** Publishers read the list for the book form; only admins change categories. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PUBLISHER')")
    public List<AdminCategoryResponse> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return service.create(request, CurrentUser.id());
    }

    @PutMapping("/{categoryId}")
    public AdminCategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return service.update(categoryId, request, CurrentUser.id());
    }
}
