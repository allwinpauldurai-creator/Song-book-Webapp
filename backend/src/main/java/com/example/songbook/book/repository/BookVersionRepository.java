package com.example.songbook.book.repository;

import com.example.songbook.book.entity.BookVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookVersionRepository extends JpaRepository<BookVersion, UUID> {

    @Query("select v from BookVersion v join fetch v.pdfAsset where v.book.id = :bookId order by v.versionNumber desc")
    List<BookVersion> findHistory(UUID bookId);

    @Query("select coalesce(max(v.versionNumber), 0) from BookVersion v where v.book.id = :bookId")
    int maxVersionNumber(UUID bookId);

    @Query("select v from BookVersion v join fetch v.pdfAsset join fetch v.book where v.id = :id and v.book.id = :bookId")
    Optional<BookVersion> findForBook(UUID id, UUID bookId);
}
