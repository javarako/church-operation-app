package com.church.operation.rest;

import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.DataOperationResponse;
import com.church.operation.entity.Member;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.service.DataManagementService;
import com.church.operation.util.DataOperationStatus;
import com.church.operation.util.DataOperationType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DataManagementControllerTest {
    @TempDir
    Path tempDirectory;

    private final DataManagementService service = mock(DataManagementService.class);
    private MockMvc mockMvc;
    private Member admin;

    @BeforeEach
    void setUp() {
        DataManagementProperties properties = new DataManagementProperties(
            tempDirectory, Duration.ofMinutes(30), DataSize.ofMegabytes(20)
        );
        mockMvc = standaloneSetup(new DataManagementController(service, properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        admin = member("admin-1", Role.ADMIN);
    }

    @Test
    void downloadsEncryptedFullBackupWithoutPuttingPasswordInUrl() throws Exception {
        Path archive = Files.writeString(tempDirectory.resolve("backup.zip"), "encrypted-backup");
        when(service.createFullBackup(any(Member.class), any(char[].class)))
            .thenReturn(new DataManagementService.DownloadArtifact(archive, "church-full-backup.zip"));

        mockMvc.perform(post("/api/admin/data-management/full-backup")
                .principal(authentication(admin))
                .contentType("application/json")
                .content("{\"password\":\"strong backup password\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"church-full-backup.zip\""))
            .andExpect(content().bytes("encrypted-backup".getBytes()));

        verify(service).createFullBackup(any(Member.class), any(char[].class));
    }

    @Test
    void validatesMultipartRestoreAndReturnsOperationSummary() throws Exception {
        when(service.validateRestore(any(Member.class), any(Path.class), any(char[].class)))
            .thenReturn(operation(DataOperationStatus.VALIDATED));

        mockMvc.perform(multipart("/api/admin/data-management/restore/validate")
                .file(new MockMultipartFile("file", "backup.zip", "application/zip", new byte[] {1, 2}))
                .param("password", "restore password")
                .principal(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("op-1"))
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.collectionCount").value(4));
    }

    @Test
    void downloadsSafetyBackupAndExecutesConfirmedRestore() throws Exception {
        Path archive = Files.writeString(tempDirectory.resolve("safety.zip"), "safety-backup");
        when(service.createSafetyBackup(any(Member.class), anyString(), any(char[].class)))
            .thenReturn(new DataManagementService.DownloadArtifact(archive, "pre-restore-safety-backup.zip"));
        when(service.executeRestore(any(Member.class), anyString(), anyString()))
            .thenReturn(operation(DataOperationStatus.COMPLETE));

        mockMvc.perform(post("/api/admin/data-management/restore/op-1/safety-backup")
                .principal(authentication(admin))
                .contentType("application/json")
                .content("{\"password\":\"safety password\"}"))
            .andExpect(status().isOk())
            .andExpect(content().bytes("safety-backup".getBytes()));

        mockMvc.perform(post("/api/admin/data-management/restore/op-1/execute")
                .principal(authentication(admin))
                .contentType("application/json")
                .content("{\"confirmation\":\"RESTORE FULL DATABASE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    @Test
    void returnsOwnedOperationStatusAndForbidsRejectedActor() throws Exception {
        when(service.status(admin, "op-1")).thenReturn(operation(DataOperationStatus.VALIDATED));

        mockMvc.perform(get("/api/admin/data-management/restore/op-1")
                .principal(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("op-1"));

        Member viewer = member("viewer-1", Role.VIEWER);
        when(service.status(viewer, "op-1")).thenThrow(new SecurityException("Administrator access is required."));
        mockMvc.perform(get("/api/admin/data-management/restore/op-1")
                .principal(authentication(viewer)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsMissingBackupPassword() throws Exception {
        mockMvc.perform(post("/api/admin/data-management/full-backup")
                .principal(authentication(admin))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    private DataOperationResponse operation(DataOperationStatus status) {
        return new DataOperationResponse(
            "op-1", DataOperationType.FULL_RESTORE, status,
            Instant.parse("2026-07-16T13:00:00Z"), 4, 25, 3, "Ready"
        );
    }

    private TestingAuthenticationToken authentication(Member member) {
        return new TestingAuthenticationToken(member, null);
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(id + "@example.test");
        member.setRoles(Set.of(role));
        return member;
    }
}
