package com.example.songbook.user.repository;

import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.role.Role;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> search(String q, AccountStatus status, RoleName role) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String pattern = PageableUtils.likePattern(q);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                    cb.like(root.get("email"), pattern, '\\')));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> sub = query.subquery(UUID.class);
                var u = sub.from(User.class);
                Join<User, Role> r = u.join("roles");
                sub.select(u.get("id")).where(cb.equal(r.get("name"), role));
                return root.get("id").in(sub);
            });
        }
        return spec;
    }
}
