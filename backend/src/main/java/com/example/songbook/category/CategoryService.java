package com.example.songbook.category;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.category.CategoryDtos.AdminCategoryResponse;
import com.example.songbook.category.CategoryDtos.CategoryResponse;
import com.example.songbook.category.CategoryDtos.CreateCategoryRequest;
import com.example.songbook.category.CategoryDtos.UpdateCategoryRequest;
import com.example.songbook.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Categories are deactivated rather than deleted, so books keep their classification history. */
@Service
public class CategoryService {

    private final BookCategoryRepository repository;
    private final AuditService audit;

    public CategoryService(BookCategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return repository.findActiveOrdered().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listAll() {
        return repository.findAllOrdered().stream().map(AdminCategoryResponse::from).toList();
    }

    @Transactional
    public AdminCategoryResponse create(CreateCategoryRequest req, UUID actorId) {
        if (repository.existsByNameIgnoreCase(req.name().trim(), null)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        BookCategory category = repository.saveAndFlush(new BookCategory(req.name(), blankToNull(req.description())));
        audit.record(actorId, AuditActions.CATEGORY_CREATED, "CATEGORY", category.getId(), Map.of("name", category.getName()));
        return AdminCategoryResponse.from(category);
    }

    @Transactional
    public AdminCategoryResponse update(UUID id, UpdateCategoryRequest req, UUID actorId) {
        BookCategory category = repository.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
        ApiException.checkVersion(req.version(), category.getVersion());
        if (repository.existsByNameIgnoreCase(req.name().trim(), id)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        category.setName(req.name());
        category.setDescription(blankToNull(req.description()));
        category.setActive(req.active());
        repository.flush();
        audit.record(actorId, AuditActions.CATEGORY_UPDATED, "CATEGORY", id,
                Map.of("name", category.getName(), "active", category.isActive()));
        return AdminCategoryResponse.from(category);
    }

    /** Resolves a category for assignment to a book; inactive categories cannot be newly assigned. */
    @Transactional(readOnly = true)
    public BookCategory requireAssignable(UUID id, UUID currentCategoryId) {
        if (id == null) {
            return null;
        }
        BookCategory category = repository.findById(id)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Category not found"));
        if (!category.isActive() && !id.equals(currentCategoryId)) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Category is inactive");
        }
        return category;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
