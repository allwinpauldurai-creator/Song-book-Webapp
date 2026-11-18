package com.example.songbook.reader.repository;

import com.example.songbook.reader.entity.ReadingProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, UUID> {

    @Query("select p from ReadingProgress p where p.user.id = :userId and p.book.id = :bookId")
    Optional<ReadingProgress> findByUserAndBook(UUID userId, UUID bookId);

    @Query(value = "select p from ReadingProgress p join fetch p.book b where p.user.id = :userId",
            countQuery = "select count(p) from ReadingProgress p where p.user.id = :userId")
    Page<ReadingProgress> findHistory(UUID userId, Pageable pageable);
}
