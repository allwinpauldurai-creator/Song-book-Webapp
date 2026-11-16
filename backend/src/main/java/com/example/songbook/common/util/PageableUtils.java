package com.example.songbook.common.util;

import com.example.songbook.common.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Restricts client-supplied sort properties to an allow-list and maps API names to entity paths,
 * so arbitrary entity attributes cannot be probed through {@code ?sort=}.
 */
public final class PageableUtils {

    private PageableUtils() {
    }

    public static Pageable sanitize(Pageable pageable, Map<String, String> allowed, Sort defaultSort) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String path = allowed.get(order.getProperty());
            if (path == null) {
                throw ApiException.badRequest("INVALID_SORT",
                        "Unsupported sort property '" + order.getProperty() + "'. Allowed: " + allowed.keySet());
            }
            sort = sort.and(Sort.by(order.getDirection(), path));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /** Escapes LIKE wildcards in user input and wraps it for a contains-match on lower-cased columns. */
    public static String likePattern(String term) {
        String escaped = term.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
