package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.YearEndClosingRequest;
import com.church.operation.dto.YearEndClosingReportStatus;
import com.church.operation.dto.YearEndClosingStatusResponse;
import com.church.operation.dto.YearlyFinancialReport;
import com.church.operation.dto.YearlyWorkbookDownload;
import com.church.operation.entity.Member;
import com.church.operation.entity.YearEndClosing;
import com.church.operation.exception.YearEndClosingConflictException;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.YearEndClosingRepository;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import com.church.operation.util.YearEndClosingStatus;
import com.church.operation.util.YearEndReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.church.operation.util.YearEndClosingStatus.CLOSED;
import static com.church.operation.util.YearEndClosingStatus.NOT_CLOSED;
import static com.church.operation.util.YearEndClosingStatus.REOPENED;
import static com.church.operation.util.YearEndReportType.EXPENDITURE;
import static com.church.operation.util.YearEndReportType.OFFERING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YearEndClosingServiceTest {
    private static final byte[] WORKBOOK = new byte[] {1, 2, 3};
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-21T19:42:00Z"),
        ZoneOffset.UTC
    );

    @Mock private YearEndClosingRepository repository;
    @Mock private YearEndSnapshotStore snapshotStore;
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private YearlyOfferingReportService offeringReportService;
    @Mock private YearlyExpenditureReportService expenditureReportService;
    @Mock private YearlyFinancialExcelService excelService;
    @Mock private SystemAuditService audit;
    @Mock private MongoTemplate mongoTemplate;

    private YearEndClosingService service;
    private Member admin;
    private YearlyFinancialReport report;

    @BeforeEach
    void setUp() {
        service = new YearEndClosingService(
            repository,
            snapshotStore,
            memberRepository,
            passwordEncoder,
            new FiscalYearProperties(1),
            offeringReportService,
            expenditureReportService,
            excelService,
            audit,
            mongoTemplate,
            CLOCK
        );
        admin = member("admin-id", "admin@church.local", Role.ADMIN);
        admin.setPasswordHash("encoded");
        report = report(2025);
    }

    @Test
    void reportsIndependentLifecycleStatesAndClosingEligibility() {
        YearEndClosing offering = closing(OFFERING, 2, CLOSED, true);
        YearEndClosing expenditure = closing(EXPENDITURE, 1, REOPENED, false);
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, OFFERING))
            .thenReturn(Optional.of(offering));
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, EXPENDITURE))
            .thenReturn(Optional.of(expenditure));

        YearEndClosingStatusResponse response = service.status(admin, 2025);

        assertThat(response.fiscalEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(response.closeEligible()).isTrue();
        assertThat(response.offering().status()).isEqualTo(CLOSED);
        assertThat(response.offering().version()).isEqualTo(2);
        assertThat(response.expenditure().status()).isEqualTo(REOPENED);
        assertThat(response.expenditure().eventAt()).isEqualTo(expenditure.getReopenedAt());
    }

    @Test
    void reportsNotClosedWhenNoHistoryExists() {
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2026, OFFERING))
            .thenReturn(Optional.empty());
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2026, EXPENDITURE))
            .thenReturn(Optional.empty());

        YearEndClosingStatusResponse response = service.status(admin, 2026);

        assertThat(response.closeEligible()).isFalse();
        assertThat(response.offering().status()).isEqualTo(NOT_CLOSED);
        assertThat(response.expenditure().status()).isEqualTo(NOT_CLOSED);
    }

    @Test
    void rejectsWrongCurrentPasswordBeforeGenerating() {
        when(memberRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", admin.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> service.close(admin, OFFERING, request("wrong")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Current password is incorrect.");

        verifyNoInteractions(offeringReportService, expenditureReportService, excelService, snapshotStore);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PASTOR", "VIEWER", "MEMBERSHIP", "MEMBER"})
    void rejectsNonFinanceRolesForClosing(Role role) {
        Member actor = member(role.name().toLowerCase() + "-id", role.name().toLowerCase() + "@church.local", role);
        when(memberRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.close(actor, OFFERING, request("secret")))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to close yearly reports.");

        verifyNoInteractions(passwordEncoder, offeringReportService, excelService, snapshotStore);
    }

    @Test
    void rejectsLockedCurrentAccount() {
        admin.setLocked(true);
        when(memberRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.close(admin, OFFERING, request("secret")))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Your account is not permitted to close yearly reports.");
    }

    @Test
    void rejectsInactiveCurrentAccount() {
        admin.setActive(false);
        when(memberRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.close(admin, OFFERING, request("secret")))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Your account is not permitted to close yearly reports.");
    }

    @Test
    void rejectsClosingBeforeFiscalYearHasEnded() {
        stubValidPassword(admin);

        assertThatThrownBy(() -> service.close(
            admin,
            OFFERING,
            new YearEndClosingRequest(2026, "secret")
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Year-end closing is available after 2026-12-31.");

        verifyNoInteractions(offeringReportService, excelService, snapshotStore);
    }

    @Test
    void closesOfferingAsVersionOneAndStoresFinalWorkbook() {
        stubValidPassword(admin);
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(offeringReportService.build(admin, 2025)).thenReturn(report);
        when(excelService.render(eq(report), any(YearlyWorkbookLifecycle.class))).thenReturn(WORKBOOK);
        when(snapshotStore.store(
            WORKBOOK,
            "yearly-offerings-2025-closed-v1.xlsx",
            2025,
            OFFERING,
            1
        )).thenReturn(new YearEndSnapshotStore.StoredSnapshot("grid-1", 3, "checksum"));
        when(repository.save(any(YearEndClosing.class))).thenAnswer(invocation -> {
            YearEndClosing closing = invocation.getArgument(0);
            closing.setId("closing-1");
            return closing;
        });

        YearEndClosingReportStatus result = service.close(admin, OFFERING, request("secret"));

        assertThat(result.status()).isEqualTo(CLOSED);
        assertThat(result.version()).isEqualTo(1);
        ArgumentCaptor<YearEndClosing> saved = ArgumentCaptor.forClass(YearEndClosing.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getActiveKey()).isEqualTo("2025:OFFERING");
        assertThat(saved.getValue().getGridFsFileId()).isEqualTo("grid-1");
        assertThat(saved.getValue().getClosedByMemberId()).isEqualTo("admin-id");
        assertThat(saved.getValue().getClosedAt()).isEqualTo(CLOCK.instant());
        verify(audit).recordSuccess(
            eq(admin),
            eq(SystemAuditOperation.YEAR_END_CLOSE),
            any(Map.class)
        );
    }

    @Test
    void deletesNewSnapshotWhenConcurrentClosingWins() {
        stubValidPassword(admin);
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(offeringReportService.build(admin, 2025)).thenReturn(report);
        when(excelService.render(eq(report), any(YearlyWorkbookLifecycle.class))).thenReturn(WORKBOOK);
        when(snapshotStore.store(any(), anyString(), eq(2025), eq(OFFERING), eq(1)))
            .thenReturn(new YearEndSnapshotStore.StoredSnapshot("orphan", 3, "checksum"));
        when(repository.save(any(YearEndClosing.class)))
            .thenThrow(new DuplicateKeyException("active closing"));

        assertThatThrownBy(() -> service.close(admin, OFFERING, request("secret")))
            .isInstanceOf(YearEndClosingConflictException.class);

        verify(snapshotStore).delete("orphan");
        verify(audit).recordFailure(
            eq(admin),
            eq(SystemAuditOperation.YEAR_END_CLOSE),
            any(Map.class),
            any(YearEndClosingConflictException.class)
        );
    }

    @Test
    void surfacesSnapshotCleanupFailureAfterClosingConflict() {
        stubValidPassword(admin);
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(offeringReportService.build(admin, 2025)).thenReturn(report);
        when(excelService.render(eq(report), any(YearlyWorkbookLifecycle.class))).thenReturn(WORKBOOK);
        when(snapshotStore.store(any(), anyString(), eq(2025), eq(OFFERING), eq(1)))
            .thenReturn(new YearEndSnapshotStore.StoredSnapshot("orphan", 3, "checksum"));
        when(repository.save(any(YearEndClosing.class)))
            .thenThrow(new DuplicateKeyException("active closing"));
        org.mockito.Mockito.doThrow(new IllegalStateException("GridFS unavailable"))
            .when(snapshotStore).delete("orphan");

        assertThatThrownBy(() -> service.close(admin, OFFERING, request("secret")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Year-end snapshot cleanup failed.")
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsTreasurerToAtomicallyReopenWithoutDeletingSnapshot() {
        Member treasurer = member("treasurer-id", "treasurer@church.local", Role.TREASURER);
        treasurer.setPasswordHash("encoded");
        stubValidPassword(treasurer);
        YearEndClosing closing = closing(OFFERING, 1, CLOSED, true);
        closing.setStatus(REOPENED);
        closing.setActive(false);
        closing.setActiveKey(null);
        closing.setReopenedByMemberId(treasurer.getId());
        closing.setReopenedByEmail(treasurer.getPrimaryEmail());
        closing.setReopenedAt(CLOCK.instant());
        when(mongoTemplate.findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(YearEndClosing.class)
        )).thenReturn(closing);

        YearEndClosingReportStatus result = service.reopen(treasurer, OFFERING, request("secret"));

        assertThat(result.status()).isEqualTo(REOPENED);
        assertThat(closing.isActive()).isFalse();
        assertThat(closing.getActiveKey()).isNull();
        assertThat(closing.getGridFsFileId()).isEqualTo("grid-1");
        assertThat(closing.getReopenedByMemberId()).isEqualTo("treasurer-id");
        assertThat(closing.getReopenedAt()).isEqualTo(CLOCK.instant());
        verify(snapshotStore, never()).delete(anyString());
        verify(audit).recordSuccess(
            eq(treasurer),
            eq(SystemAuditOperation.YEAR_END_REOPEN),
            any(Map.class)
        );
    }

    @Test
    void rejectsReopenWhenAnotherRequestAlreadyReopenedTheReport() {
        stubValidPassword(admin);
        when(mongoTemplate.findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(YearEndClosing.class)
        )).thenReturn(null);

        assertThatThrownBy(() -> service.reopen(admin, OFFERING, request("secret")))
            .isInstanceOf(YearEndClosingConflictException.class)
            .hasMessage("This yearly report is not currently closed.");

        verify(audit).recordFailure(
            eq(admin),
            eq(SystemAuditOperation.YEAR_END_REOPEN),
            any(Map.class),
            any(YearEndClosingConflictException.class)
        );
    }

    @Test
    void downloadsExactClosedSnapshotWithoutRegeneration() {
        Member viewer = member("viewer-id", "viewer@church.local", Role.VIEWER);
        YearEndClosing closing = closing(OFFERING, 2, CLOSED, true);
        closing.setFilename("yearly-offerings-2025-closed-v2.xlsx");
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, OFFERING))
            .thenReturn(Optional.of(closing));
        when(snapshotStore.load(closing)).thenReturn(WORKBOOK);

        YearlyWorkbookDownload download = service.download(viewer, OFFERING, 2025);

        assertThat(download.content()).isEqualTo(WORKBOOK);
        assertThat(download.filename()).isEqualTo("yearly-offerings-2025-closed-v2.xlsx");
        verifyNoInteractions(offeringReportService, excelService);
    }

    @Test
    void generatesReopenedDownloadAsLiveDraft() {
        Member viewer = member("viewer-id", "viewer@church.local", Role.VIEWER);
        YearEndClosing reopened = closing(OFFERING, 1, REOPENED, false);
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, OFFERING))
            .thenReturn(Optional.empty());
        when(repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(2025, OFFERING))
            .thenReturn(Optional.of(reopened));
        when(offeringReportService.build(viewer, 2025)).thenReturn(report);
        when(excelService.render(eq(report), any(YearlyWorkbookLifecycle.class))).thenReturn(WORKBOOK);

        YearlyWorkbookDownload download = service.download(viewer, OFFERING, 2025);

        assertThat(download.filename()).isEqualTo("yearly-offerings-2025-draft.xlsx");
        ArgumentCaptor<YearlyWorkbookLifecycle> lifecycle =
            ArgumentCaptor.forClass(YearlyWorkbookLifecycle.class);
        verify(excelService).render(eq(report), lifecycle.capture());
        assertThat(lifecycle.getValue().status()).isEqualTo(REOPENED);
        assertThat(lifecycle.getValue().eventAt()).isEqualTo(reopened.getReopenedAt());
    }

    @Test
    void doesNotRegenerateWhenClosedSnapshotLoadFails() {
        Member viewer = member("viewer-id", "viewer@church.local", Role.VIEWER);
        YearEndClosing closing = closing(EXPENDITURE, 1, CLOSED, true);
        when(repository.findByFiscalYearAndReportTypeAndActiveTrue(2025, EXPENDITURE))
            .thenReturn(Optional.of(closing));
        when(snapshotStore.load(closing)).thenThrow(new IllegalStateException("checksum failed"));

        assertThatThrownBy(() -> service.download(viewer, EXPENDITURE, 2025))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("checksum failed");

        verifyNoInteractions(expenditureReportService, excelService);
    }

    private void stubValidPassword(Member member) {
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("secret", member.getPasswordHash())).thenReturn(true);
    }

    private YearEndClosingRequest request(String password) {
        return new YearEndClosingRequest(2025, password);
    }

    private YearEndClosing closing(
        YearEndReportType type,
        int version,
        YearEndClosingStatus status,
        boolean active
    ) {
        YearEndClosing closing = new YearEndClosing();
        closing.setId("closing-" + version);
        closing.setFiscalYear(2025);
        closing.setReportType(type);
        closing.setVersion(version);
        closing.setStatus(status);
        closing.setActive(active);
        closing.setActiveKey(active ? YearEndClosing.activeKey(2025, type) : null);
        closing.setGridFsFileId("grid-" + version);
        closing.setFilename("yearly-" + type.name().toLowerCase() + "-2025-closed-v" + version + ".xlsx");
        closing.setChecksum("checksum");
        closing.setClosedAt(Instant.parse("2026-01-02T10:00:00Z"));
        closing.setReopenedAt(Instant.parse("2026-01-03T10:00:00Z"));
        return closing;
    }

    private YearlyFinancialReport report(int year) {
        return new YearlyFinancialReport(
            year,
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 12, 31),
            List.of(),
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            false,
            "Offering income",
            "수입 결산 및 예산안",
            "수입결산",
            "수입대비",
            "전년도 이월금"
        );
    }

    private Member member(String id, String email, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(email);
        member.setRoles(Set.of(role));
        member.setActive(true);
        member.setLocked(false);
        return member;
    }
}
