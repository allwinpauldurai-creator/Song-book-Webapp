package com.example.songbook.user.repository;

import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    @Query("""
            select count(u) from User u join u.roles r
            where r.name = :role and u.accountStatus = com.example.songbook.user.entity.AccountStatus.ACTIVE
            """)
    long countActiveWithRole(RoleName role);

    @Query("select count(u) from User u join u.roles r where r.name = :role")
    long countWithRole(RoleName role);
}
