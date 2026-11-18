package com.example.songbook.book.repository;

import com.example.songbook.book.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

    @Override
    @EntityGraph(attributePaths = {"category", "coverAsset"})
    Page<Book> findAll(Specification<Book> spec, Pageable pageable);

    @Query("""
            select b from Book b
            left join fetch b.category
            left join fetch b.coverAsset
            left join fetch b.currentVersion v
            left join fetch v.pdfAsset
            where b.id = :id
            """)
    Optional<Book> findDetailed(UUID id);

    /** Serializes version uploads for one book. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> lockById(UUID id);

    long countByCategoryId(UUID categoryId);
}
