package com.example.songbook.role;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);

    /**
     * Locks the role row. Used to serialize changes that could remove the last active admin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.name = :name")
    Optional<Role> lockByName(RoleName name);

    default Role require(RoleName name) {
        return findByName(name).orElseThrow(() -> new IllegalStateException("Role not seeded: " + name));
    }
}
