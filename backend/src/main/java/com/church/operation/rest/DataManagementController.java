package com.church.operation.rest;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.BackupRequest;
import com.church.operation.dto.DataOperationResponse;
import com.church.operation.dto.RestoreExecuteRequest;
import com.church.operation.dto.FiscalArchivePreview;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.entity.Member;
import com.church.operation.service.DataManagementService;
import com.church.operation.service.FiscalArchiveService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/admin/data-management")
public class DataManagementController {
    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final DataManagementService dataManagementService;
    private final FiscalArchiveService fiscalArchiveService;
    private final DataManagementProperties properties;

    public DataManagementController(
        DataManagementService dataManagementService,
        FiscalArchiveService fiscalArchiveService,
        DataManagementProperties properties
    ) {
        this.dataManagementService = dataManagementService;
        this.fiscalArchiveService = fiscalArchiveService;
        this.properties = properties;
    }

    @PostMapping("/full-backup")
    ResponseEntity<Resource> fullBackup(
        Authentication authentication,
        @Valid @RequestBody BackupRequest request
    ) throws IOException {
        return download(dataManagementService.createFullBackup(
            actor(authentication), request.password().toCharArray()
        ));
    }

    @PostMapping(path = "/restore/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DataOperationResponse validateRestore(
        Authentication authentication,
        @RequestPart("file") MultipartFile file,
        @RequestParam("password") String password
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Backup archive is required.");
        }
        if (file.getSize() > properties.maxUploadSize().toBytes()) {
            throw new IllegalArgumentException("Backup archive exceeds the configured upload limit.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Backup password is required.");
        }
        Files.createDirectories(properties.tempDirectory());
        Path staged = Files.createTempFile(properties.tempDirectory(), "restore-upload-", ".zip");
        try {
            file.transferTo(staged);
            return dataManagementService.validateRestore(
                actor(authentication), staged, password.toCharArray()
            );
        } catch (IOException | RuntimeException | Error exception) {
            Files.deleteIfExists(staged);
            throw exception;
        }
    }

    @PostMapping("/restore/{id}/safety-backup")
    ResponseEntity<Resource> safetyBackup(
        Authentication authentication,
        @PathVariable("id") String id,
        @Valid @RequestBody BackupRequest request
    ) throws IOException {
        return download(dataManagementService.createSafetyBackup(
            actor(authentication), id, request.password().toCharArray()
        ));
    }

    @PostMapping("/restore/{id}/execute")
    DataOperationResponse executeRestore(
        Authentication authentication,
        @PathVariable("id") String id,
        @Valid @RequestBody RestoreExecuteRequest request
    ) throws IOException {
        return dataManagementService.executeRestore(actor(authentication), id, request.confirmation());
    }

    @GetMapping("/restore/{id}")
    DataOperationResponse restoreStatus(
        Authentication authentication,
        @PathVariable("id") String id
    ) {
        return dataManagementService.status(actor(authentication), id);
    }

    @GetMapping("/fiscal/{year}/preview")
    FiscalArchivePreview fiscalPreview(Authentication authentication, @PathVariable("year") int year) {
        return fiscalArchiveService.preview(actor(authentication), year);
    }

    @PostMapping("/fiscal/{year}/archive")
    ResponseEntity<Resource> fiscalArchive(
        Authentication authentication,
        @PathVariable("year") int year,
        @Valid @RequestBody BackupRequest request
    ) throws IOException {
        FiscalArchiveService.DownloadArtifact artifact = fiscalArchiveService.createArchive(
            actor(authentication), year, request.password().toCharArray()
        );
        return download(artifact);
    }

    @PostMapping("/fiscal/{id}/clean")
    FiscalArchiveRegistry cleanFiscalArchive(
        Authentication authentication,
        @PathVariable("id") String id,
        @Valid @RequestBody RestoreExecuteRequest request
    ) {
        return fiscalArchiveService.clean(actor(authentication), id, request.confirmation());
    }

    @PostMapping(path = "/fiscal/restore/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FiscalArchiveService.RestorePreview validateFiscalRestore(Authentication authentication,
        @RequestPart("file") MultipartFile file, @RequestParam("password") String password) throws IOException {
        if (file == null || file.isEmpty() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Fiscal archive and password are required.");
        }
        if (file.getSize() > properties.maxUploadSize().toBytes()) {
            throw new IllegalArgumentException("Fiscal archive exceeds the configured upload limit.");
        }
        Files.createDirectories(properties.tempDirectory());
        Path staged = Files.createTempFile(properties.tempDirectory(), "fiscal-restore-", ".zip");
        try {
            file.transferTo(staged);
            return fiscalArchiveService.validateRestore(actor(authentication), staged, password.toCharArray());
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    @PostMapping("/fiscal/restore/{id}/execute")
    FiscalArchiveRegistry executeFiscalRestore(Authentication authentication, @PathVariable("id") String id,
        @Valid @RequestBody RestoreExecuteRequest request) {
        return fiscalArchiveService.executeRestore(actor(authentication), id, request.confirmation());
    }

    private ResponseEntity<Resource> download(DataManagementService.DownloadArtifact artifact) throws IOException {
        long size = Files.size(artifact.path());
        InputStream stream = new FilterInputStream(Files.newInputStream(artifact.path())) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    artifact.close();
                }
            }
        };
        Resource resource = new InputStreamResource(stream) {
            @Override
            public long contentLength() {
                return size;
            }
        };
        return ResponseEntity.ok()
            .contentType(ZIP)
            .contentLength(size)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(artifact.filename()).build().toString()
            )
            .body(resource);
    }

    private ResponseEntity<Resource> download(FiscalArchiveService.DownloadArtifact artifact) throws IOException {
        long size = Files.size(artifact.path());
        InputStream stream = new FilterInputStream(Files.newInputStream(artifact.path())) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    artifact.close();
                }
            }
        };
        Resource resource = new InputStreamResource(stream) {
            @Override
            public long contentLength() {
                return size;
            }
        };
        return ResponseEntity.ok()
            .contentType(ZIP)
            .contentLength(size)
            .header("X-Fiscal-Archive-Id", artifact.archiveId())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(artifact.filename()).build().toString()
            )
            .body(resource);
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
