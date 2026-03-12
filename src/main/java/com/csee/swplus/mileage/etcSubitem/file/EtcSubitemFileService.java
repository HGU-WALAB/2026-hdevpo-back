package com.csee.swplus.mileage.etcSubitem.file;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
//import java.nio.file.StandardCopyOption; // 파일 복사 관련 설정 제공 e.g. REPLACE_EXISTING
//import java.nio.file.attribute.PosixFilePermission;
//import java.nio.file.attribute.PosixFilePermissions;
//import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class EtcSubitemFileService {
    @Value("${file.upload-dir}")
    @Getter
    private String uploadDir;

    private static final String ALLOWED_EXTENSION = "pdf";

    /**
     * Saves file with a safe UUID-based filename to prevent path traversal on write.
     * Only .pdf is allowed. Never uses user-supplied filename for storage path.
     */
    public String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }
        String ext = "";
        if (originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_EXTENSION.equals(ext)) {
            throw new IllegalArgumentException("Only .pdf files are allowed");
        }

        File directory = new File(uploadDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String safeFilename = UUID.randomUUID().toString() + "." + ALLOWED_EXTENSION;
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetLocation = baseDir.resolve(safeFilename).normalize();
        if (!targetLocation.startsWith(baseDir)) {
            throw new SecurityException("Path traversal attempt blocked");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation);
        }

        return safeFilename;
    }

    public void deleteFile(String filename) {
        if (filename == null || filename.isEmpty() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(filename).normalize();
            if (!filePath.startsWith(baseDir)) {
                throw new SecurityException("Path traversal attempt blocked");
            }
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("파일 삭제 중 오류 발생: {}", filename, e);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.");
        }
    }

    public String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
