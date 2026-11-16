package com.example.songbook.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BookCategoryRepository extends JpaRepository<BookCategory, UUID> {

    @Query("select c from BookCategory c where c.active = true order by lower(c.name)")
    List<BookCategory> findActiveOrdered();

    @Query("select c from BookCategory c order by lower(c.name)")
    List<BookCategory> findAllOrdered();

    @Query("select count(c) > 0 from BookCategory c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByNameIgnoreCase(String name, UUID excludeId);
}
