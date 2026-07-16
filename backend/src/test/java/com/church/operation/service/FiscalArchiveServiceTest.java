package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.FiscalArchivePreview;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.FiscalArchiveRegistryRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class FiscalArchiveServiceTest {
    @TempDir
    Path tempDirectory;
    private final OfferingRepository offerings = mock(OfferingRepository.class);
    private final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final FiscalArchiveRegistryRepository registries = mock(FiscalArchiveRegistryRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final ReferenceDataRepository references = mock(ReferenceDataRepository.class);
    private final DataOperationStore operations = mock(DataOperationStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-04-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void previewsInclusiveNonJanuaryFiscalRangeAndAllArchivedRecordTypes() {
        FiscalArchiveService service = service(4);
        Offering live = offering("offering-live");
        Offering deleted = offering("offering-deleted");
        deleted.setDeleted(true);
        FinancialTransaction linkedLive = transaction("income-live");
        FinancialTransaction linkedDeleted = transaction("income-deleted");
        linkedDeleted.setDeleted(true);
        FinancialTransaction expense = transaction("expense");
        Budget budget = new Budget();
        budget.setId("budget");

        when(offerings.findAllByOfferingDateBetween(
            LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of(live, deleted));
        when(transactions.findOfferingTransactionsBySourceIds(List.of("offering-live", "offering-deleted")))
            .thenReturn(List.of(linkedLive, linkedDeleted));
        when(transactions.findManualExpensesByTransactionDateBetween(
            LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of(expense));
        when(budgets.findAllByFiscalYear(2026)).thenReturn(List.of(budget));

        FiscalArchivePreview preview = service.preview(admin(), 2026);

        assertThat(preview.fiscalYear()).isEqualTo(2026);
        assertThat(preview.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(preview.endDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(preview.offeringCount()).isEqualTo(2);
        assertThat(preview.linkedIncomeCount()).isEqualTo(2);
        assertThat(preview.expenseCount()).isEqualTo(1);
        assertThat(preview.budgetCount()).isEqualTo(1);
        assertThat(preview.totalRecordCount()).isEqualTo(6);
    }

    @Test
    void previewsCalendarFiscalYearWhenStartMonthIsJanuary() {
        FiscalArchiveService service = service(1);
        when(offerings.findAllByOfferingDateBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of());
        when(transactions.findOfferingTransactionsBySourceIds(List.of())).thenReturn(List.of());
        when(transactions.findManualExpensesByTransactionDateBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of());
        when(budgets.findAllByFiscalYear(2026)).thenReturn(List.of());

        FiscalArchivePreview preview = service.preview(admin(), 2026);

        assertThat(preview.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(preview.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        verify(offerings).findAllByOfferingDateBetween(preview.startDate(), preview.endDate());
    }

    @Test
    void requiresCompletedDownloadAndExactPhraseBeforeIdempotentCleanup() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Member admin = admin();
        Offering offering = offering("offering-1");
        FinancialTransaction linked = transaction("income-1");
        FinancialTransaction expense = transaction("expense-1");
        Budget budget = new Budget();
        budget.setId("budget-1");
        when(offerings.findAllByOfferingDateBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering));
        when(transactions.findOfferingTransactionsBySourceIds(List.of("offering-1"))).thenReturn(List.of(linked));
        when(transactions.findManualExpensesByTransactionDateBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(expense));
        when(budgets.findAllByFiscalYear(2026)).thenReturn(List.of(budget));
        when(codec.write(any(), any(), any())).thenReturn("archive-checksum");
        when(registries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FiscalArchiveService.DownloadArtifact download = service.createArchive(
            admin, 2026, "archive-password".toCharArray()
        );

        assertThatThrownBy(() -> service.clean(admin, download.archiveId(), "CLEAN FISCAL YEAR 2026"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("download");
        download.close();
        assertThatThrownBy(() -> service.clean(admin, download.archiveId(), "wrong"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmation");

        FiscalArchiveRegistry cleaned = service.clean(admin, download.archiveId(), "CLEAN FISCAL YEAR 2026");

        assertThat(cleaned.getStatus().name()).isEqualTo("CLEANED");
        verify(transactions).deleteAllById(List.of("income-1", "expense-1"));
        verify(offerings).deleteAllById(List.of("offering-1"));
        verify(budgets).deleteAllById(List.of("budget-1"));
        verify(operations).beginExternalMutation("fiscal-clean:" + download.archiveId());
        verify(operations).endExternalMutation("fiscal-clean:" + download.archiveId());
    }

    @Test
    void protectsReferenceCodesUsedOnlyByArchivedBudgets() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Budget offeringBudget = new Budget();
        offeringBudget.setId("offering-budget");
        offeringBudget.setBudgetType(com.church.operation.util.BudgetType.OFFERING_INCOME);
        offeringBudget.setCategory("GENERAL");
        Budget expenseBudget = new Budget();
        expenseBudget.setId("expense-budget");
        expenseBudget.setBudgetType(com.church.operation.util.BudgetType.EXPENSE);
        expenseBudget.setCategory("OFFICE");
        expenseBudget.setSubCategory("SUPPLIES");
        when(offerings.findAllByOfferingDateBetween(any(), any())).thenReturn(List.of());
        when(transactions.findOfferingTransactionsBySourceIds(List.of())).thenReturn(List.of());
        when(transactions.findManualExpensesByTransactionDateBetween(any(), any())).thenReturn(List.of());
        when(budgets.findAllByFiscalYear(2026)).thenReturn(List.of(offeringBudget, expenseBudget));
        when(codec.write(any(), any(), any())).thenReturn("checksum");
        when(registries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createArchive(admin(), 2026, "archive-password".toCharArray());

        org.mockito.ArgumentCaptor<FiscalArchiveRegistry> registryCaptor =
            org.mockito.ArgumentCaptor.forClass(FiscalArchiveRegistry.class);
        verify(registries, atLeastOnce()).save(registryCaptor.capture());
        FiscalArchiveRegistry registry = registryCaptor.getValue();
        assertThat(registry.getFundCategories()).containsExactly("GENERAL");
        assertThat(registry.getCategories()).containsExactly("OFFICE");
        assertThat(registry.getSubCategories()).containsExactly("SUPPLIES");
    }

    @Test
    void validatesRegistryAndDuplicateIdsBeforeMergeRestore() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Member admin = admin();
        Path upload = tempDirectory.resolve("uploaded.zip");
        java.nio.file.Files.writeString(upload, "encrypted");
        Offering offering = offering("offering-restore");
        offering.setMemberId("member-restore");
        offering.setFundCategory("GENERAL");
        FinancialTransaction income = transaction("income-restore");
        Budget budget = new Budget();
        budget.setId("budget-restore");
        FiscalArchivePayload payload = new FiscalArchivePayload(
            "archive-restore", 2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            List.of(offering), List.of(income), List.of(), List.of(budget)
        );
        when(codec.validate(upload, "archive-password".toCharArray()))
            .thenReturn(new FiscalArchiveCodec.Validated(payload, "matching-checksum"));
        FiscalArchiveRegistry registry = new FiscalArchiveRegistry();
        registry.setArchiveId("archive-restore");
        registry.setChecksum("matching-checksum");
        registry.setStatus(com.church.operation.util.FiscalArchiveStatus.CLEANED);
        when(registries.findByArchiveId("archive-restore")).thenReturn(Optional.of(registry));
        when(registries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(members.existsById("member-restore")).thenReturn(true);
        when(references.existsByTypeAndCode(com.church.operation.util.ReferenceDataType.OFFERING_FUND_CATEGORY, "GENERAL"))
            .thenReturn(true);

        FiscalArchiveService.RestorePreview preview = service.validateRestore(
            admin, upload, "archive-password".toCharArray()
        );
        FiscalArchiveRegistry restored = service.executeRestore(
            admin, preview.id(), "RESTORE FISCAL YEAR 2026"
        );

        assertThat(restored.getStatus()).isEqualTo(com.church.operation.util.FiscalArchiveStatus.RESTORED);
        verify(offerings).saveAll(List.of(offering));
        verify(transactions).saveAll(List.of(income));
        verify(budgets).saveAll(List.of(budget));

        registry.setStatus(com.church.operation.util.FiscalArchiveStatus.CLEANED);
        when(offerings.existsById("offering-restore")).thenReturn(true);
        assertThatThrownBy(() -> service.validateRestore(admin, upload, "archive-password".toCharArray()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("conflict");
        verify(offerings, never()).deleteById("offering-restore");
    }

    @Test
    void blocksRestoreWhenArchivedMemberOrReferenceDependencyIsMissing() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Offering offering = offering("offering-1");
        offering.setMemberId("missing-member");
        offering.setFundCategory("GENERAL");
        FiscalArchivePayload payload = new FiscalArchivePayload(
            "archive-1", 2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            List.of(offering), List.of(), List.of(), List.of()
        );
        Path upload = java.nio.file.Files.writeString(tempDirectory.resolve("missing-dependency.zip"), "zip");
        when(codec.validate(any(), any())).thenReturn(new FiscalArchiveCodec.Validated(payload, "checksum"));
        FiscalArchiveRegistry registry = new FiscalArchiveRegistry();
        registry.setArchiveId("archive-1");
        registry.setChecksum("checksum");
        registry.setStatus(com.church.operation.util.FiscalArchiveStatus.CLEANED);
        when(registries.findByArchiveId("archive-1")).thenReturn(Optional.of(registry));

        assertThatThrownBy(() -> service.validateRestore(admin(), upload, "password".toCharArray()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("member");
    }

    @Test
    void blocksRestoreWhenABudgetBusinessKeyAlreadyExists() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Budget archived = new Budget();
        archived.setId("archived-budget");
        archived.setFiscalYear(2026);
        archived.setBudgetType(com.church.operation.util.BudgetType.EXPENSE);
        archived.setCategory("OFFICE");
        archived.setSubCategory("SUPPLIES");
        Budget existing = new Budget();
        existing.setId("existing-budget");
        existing.setFiscalYear(2026);
        existing.setBudgetType(com.church.operation.util.BudgetType.EXPENSE);
        existing.setCategory("OFFICE");
        existing.setSubCategory("SUPPLIES");
        FiscalArchivePayload payload = new FiscalArchivePayload(
            "archive-budget", 2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            List.of(), List.of(), List.of(), List.of(archived)
        );
        Path upload = java.nio.file.Files.writeString(tempDirectory.resolve("budget-conflict.zip"), "zip");
        when(codec.validate(any(), any())).thenReturn(new FiscalArchiveCodec.Validated(payload, "checksum"));
        FiscalArchiveRegistry registry = new FiscalArchiveRegistry();
        registry.setArchiveId("archive-budget");
        registry.setChecksum("checksum");
        registry.setStatus(com.church.operation.util.FiscalArchiveStatus.CLEANED);
        when(registries.findByArchiveId("archive-budget")).thenReturn(Optional.of(registry));
        when(references.existsByTypeAndCode(
            com.church.operation.util.ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"
        )).thenReturn(true);
        when(references.existsByTypeAndCode(
            com.church.operation.util.ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"
        )).thenReturn(true);
        when(budgets.findAllByFiscalYear(2026)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.validateRestore(admin(), upload, "password".toCharArray()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("budget");
    }

    @Test
    void blocksRestoreWhenAnArchivedBudgetReferenceIsMissing() throws Exception {
        FiscalArchiveCodec codec = mock(FiscalArchiveCodec.class);
        FiscalArchiveService service = service(1, codec);
        Budget archived = new Budget();
        archived.setId("archived-budget");
        archived.setFiscalYear(2026);
        archived.setBudgetType(com.church.operation.util.BudgetType.EXPENSE);
        archived.setCategory("MISSING-CATEGORY");
        FiscalArchivePayload payload = new FiscalArchivePayload(
            "archive-budget-reference", 2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            List.of(), List.of(), List.of(), List.of(archived)
        );
        Path upload = java.nio.file.Files.writeString(tempDirectory.resolve("budget-reference.zip"), "zip");
        when(codec.validate(any(), any())).thenReturn(new FiscalArchiveCodec.Validated(payload, "checksum"));
        FiscalArchiveRegistry registry = new FiscalArchiveRegistry();
        registry.setArchiveId("archive-budget-reference");
        registry.setChecksum("checksum");
        registry.setStatus(com.church.operation.util.FiscalArchiveStatus.CLEANED);
        when(registries.findByArchiveId("archive-budget-reference")).thenReturn(Optional.of(registry));

        assertThatThrownBy(() -> service.validateRestore(admin(), upload, "password".toCharArray()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("financial category");
    }

    @Test
    void blocksFiscalPreviewForNonAdministrators() {
        Member viewer = new Member();
        viewer.setId("viewer-id");
        viewer.setRoles(Set.of(com.church.operation.util.Role.VIEWER));

        assertThatThrownBy(() -> service(1).preview(viewer, 2026))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Administrator");
    }

    private FiscalArchiveService service(int startMonth) {
        return service(startMonth, mock(FiscalArchiveCodec.class));
    }

    private FiscalArchiveService service(int startMonth, FiscalArchiveCodec codec) {
        return new FiscalArchiveService(
            offerings, transactions, budgets, registries, new FiscalYearProperties(startMonth),
            members, references, operations, codec, tempDirectory, clock
        );
    }

    private Member admin() {
        Member member = new Member();
        member.setId("admin-id");
        member.setRoles(Set.of(com.church.operation.util.Role.ADMIN));
        return member;
    }

    private Offering offering(String id) {
        Offering offering = new Offering();
        offering.setId(id);
        return offering;
    }

    private FinancialTransaction transaction(String id) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(id);
        return transaction;
    }
}
