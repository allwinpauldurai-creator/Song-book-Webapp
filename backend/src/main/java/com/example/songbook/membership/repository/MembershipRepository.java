package com.example.songbook.membership.repository;

import com.example.songbook.membership.domain.MembershipStatus;
import com.example.songbook.membership.entity.Membership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID>, JpaSpecificationExecutor<Membership> {

    @Query("select m from Membership m where m.user.id = :userId and m.status = :status")
    Optional<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    @Query("select m from Membership m where m.user.id = :userId order by m.startDate desc, m.createdAt desc")
    List<Membership> findHistory(UUID userId);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Membership> findAll(Specification<Membership> spec, Pageable pageable);
}
