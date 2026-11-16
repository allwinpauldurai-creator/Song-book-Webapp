package com.example.songbook.file.repository;

import com.example.songbook.file.entity.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
}
