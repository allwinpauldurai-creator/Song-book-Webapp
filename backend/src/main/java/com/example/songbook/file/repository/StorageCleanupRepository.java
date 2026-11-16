package com.example.songbook.file.repository;

import com.example.songbook.file.entity.StorageCleanupItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StorageCleanupRepository extends JpaRepository<StorageCleanupItem, UUID> {

    @Query("select i from StorageCleanupItem i where i.processedAt is null and i.attempts < :maxAttempts order by i.createdAt")
    List<StorageCleanupItem> findPending(int maxAttempts, Pageable pageable);
}
