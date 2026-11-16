package com.example.songbook.modification.repository;

import com.example.songbook.modification.entity.ModificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ModificationRequestRepository extends JpaRepository<ModificationRequest, UUID> {

    @Query(value = """
            select r from ModificationRequest r join fetch r.requestedBy left join fetch r.book
            where r.requestedBy.id = :userId
            """,
            countQuery = "select count(r) from ModificationRequest r where r.requestedBy.id = :userId")
    Page<ModificationRequest> findByRequester(UUID userId, Pageable pageable);

    @Query(value = """
            select r from ModificationRequest r join fetch r.requestedBy left join fetch r.book
            where (:status is null or r.status = :status)
            """,
            countQuery = "select count(r) from ModificationRequest r where (:status is null or r.status = :status)")
    Page<ModificationRequest> search(ModificationRequest.Status status, Pageable pageable);
}
