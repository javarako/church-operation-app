package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.config.DataManagementProperties;
import com.church.operation.dto.FiscalArchivePreview;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Offering;
import com.church.operation.entity.Member;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.FiscalArchiveRegistryRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.ReferenceDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.church.operation.util.FiscalArchiveStatus;
import com.church.operation.util.Role;
import com.church.operation.util.ReferenceDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FiscalArchiveService {
    private final OfferingRepository offerings;
    private final FinancialTransactionRepository transactions;
    private final BudgetRepository budgets;
    private final FiscalArchiveRegistryRepository registries;
    private final FiscalYearProperties fiscalYearProperties;
    private final Clock clock;
    private final MemberRepository members;
    private final ReferenceDataRepository references;
    private final DataOperationStore operationStore;
    private final FiscalArchiveCodec codec;
    private final Path tempDirectory;
    private final Map<String, StagedArchive> stagedArchives = new ConcurrentHashMap<>();
    private final Map<String, StagedRestore> stagedRestores = new ConcurrentHashMap<>();

    @Autowired
    public FiscalArchiveService(
        OfferingRepository offerings,
        FinancialTransactionRepository transactions,
        BudgetRepository budgets,
        FiscalArchiveRegistryRepository registries,
        FiscalYearProperties fiscalYearProperties,
        MemberRepository members,
        ReferenceDataRepository references,
        DataOperationStore operationStore,
        FiscalArchiveCodec codec,
        DataManagementProperties properties
    ) {
        this(offerings, transactions, budgets, registries, fiscalYearProperties, members, references, operationStore,
            codec, properties.tempDirectory(), Clock.systemUTC());
    }

    FiscalArchiveService(
        OfferingRepository offerings,
        FinancialTransactionRepository transactions,
        BudgetRepository budgets,
        FiscalArchiveRegistryRepository registries,
        FiscalYearProperties fiscalYearProperties,
        MemberRepository members,
        ReferenceDataRepository references,
        DataOperationStore operationStore,
        FiscalArchiveCodec codec,
        Path tempDirectory,
        Clock clock
    ) {
        this.offerings = offerings;
        this.transactions = transactions;
        this.budgets = budgets;
        this.registries = registries;
        this.fiscalYearProperties = fiscalYearProperties;
        this.clock = clock;
        this.members = members;
        this.references = references;
        this.operationStore = operationStore;
        this.codec = codec;
        this.tempDirectory = tempDirectory;
    }

    public FiscalArchivePreview preview(Member actor, int fiscalYear) {
        requireAdmin(actor);
        FiscalArchivePayload payload = select(fiscalYear, UUID.randomUUID().toString());
        return new FiscalArchivePreview(
            fiscalYear, payload.startDate(), payload.endDate(), payload.offerings().size(),
            payload.linkedIncome().size(), payload.expenses().size(), payload.budgets().size(),
            (long) payload.offerings().size() + payload.linkedIncome().size()
                + payload.expenses().size() + payload.budgets().size()
        );
    }

    public DownloadArtifact createArchive(Member actor, int fiscalYear, char[] password) throws IOException {
        requireAdmin(actor);
        requirePassword(password);
        String archiveId = UUID.randomUUID().toString();
        FiscalArchivePayload payload = select(fiscalYear, archiveId);
        Files.createDirectories(tempDirectory);
        Path output = Files.createTempFile(tempDirectory, "fiscal-archive-", ".zip");
        try {
            String checksum = codec.write(output, password, payload);
            FiscalArchiveRegistry registry = registry(actor, payload, checksum);
            registries.save(registry);
            StagedArchive staged = new StagedArchive(actor.getId(), payload, registry);
            stagedArchives.put(archiveId, staged);
            return new DownloadArtifact(output, "church-fiscal-" + fiscalYear + ".zip", archiveId, () -> {
                staged.downloaded = true;
                registry.setDownloaded(true);
                registries.save(registry);
            });
        } catch (IOException | RuntimeException | Error exception) {
            Files.deleteIfExists(output);
            throw exception;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public FiscalArchiveRegistry clean(Member actor, String archiveId, String confirmation) {
        requireAdmin(actor);
        StagedArchive staged = requireStaged(actor, archiveId);
        String expected = "CLEAN FISCAL YEAR " + staged.payload.fiscalYear();
        if (!expected.equals(confirmation)) {
            throw new IllegalArgumentException("The cleanup confirmation phrase does not match.");
        }
        if (!staged.downloaded) {
            throw new IllegalStateException("Complete the archive download before cleaning fiscal data.");
        }
        if (staged.registry.getStatus() == FiscalArchiveStatus.CLEANED) {
            return staged.registry;
        }
        String mutationId = "fiscal-clean:" + archiveId;
        operationStore.beginExternalMutation(mutationId);
        try {
            transactions.deleteAllById(java.util.stream.Stream.concat(
                staged.payload.linkedIncome().stream(), staged.payload.expenses().stream()
            ).map(FinancialTransaction::getId).toList());
            offerings.deleteAllById(staged.payload.offerings().stream().map(Offering::getId).toList());
            budgets.deleteAllById(staged.payload.budgets().stream().map(Budget::getId).toList());
            boolean remaining = staged.payload.offerings().stream().anyMatch(value -> offerings.existsById(value.getId()))
                || java.util.stream.Stream.concat(staged.payload.linkedIncome().stream(), staged.payload.expenses().stream())
                    .anyMatch(value -> transactions.existsById(value.getId()))
                || staged.payload.budgets().stream().anyMatch(value -> budgets.existsById(value.getId()));
            if (remaining) {
                throw new IllegalStateException("Fiscal cleanup did not remove every archived record. It can be retried safely.");
            }
            staged.registry.setStatus(FiscalArchiveStatus.CLEANED);
            staged.registry.setCleanedAt(clock.instant());
            return registries.save(staged.registry);
        } finally {
            operationStore.endExternalMutation(mutationId);
        }
    }

    public RestorePreview validateRestore(Member actor, Path archive, char[] password) throws IOException {
        requireAdmin(actor);
        requirePassword(password);
        try {
            FiscalArchiveCodec.Validated validated = codec.validate(archive, password);
            FiscalArchivePayload payload = validated.payload();
            FiscalArchiveRegistry registry = registries.findByArchiveId(payload.archiveId())
                .orElseThrow(() -> new IllegalStateException("Matching fiscal archive registry was not found."));
            if (registry.getStatus() != FiscalArchiveStatus.CLEANED
                || !validated.checksum().equals(registry.getChecksum())) {
                throw new IllegalStateException("Fiscal archive registry checksum or status does not match.");
            }
            validateDependencies(payload);
            Set<BudgetKey> existingBudgetKeys = budgets.findAllByFiscalYear(payload.fiscalYear()).stream()
                .map(BudgetKey::from).collect(java.util.stream.Collectors.toSet());
            if (payload.budgets().stream().map(BudgetKey::from).anyMatch(existingBudgetKeys::contains)) {
                throw new IllegalStateException("Fiscal archive conflicts with an existing budget business key.");
            }
            boolean conflict = payload.offerings().stream().anyMatch(value -> offerings.existsById(value.getId()))
                || java.util.stream.Stream.concat(payload.linkedIncome().stream(), payload.expenses().stream())
                    .anyMatch(value -> transactions.existsById(value.getId()))
                || payload.budgets().stream().anyMatch(value -> budgets.existsById(value.getId()));
            if (conflict) {
                throw new IllegalStateException("Fiscal archive conflicts with existing records.");
            }
            String id = UUID.randomUUID().toString();
            stagedRestores.put(id, new StagedRestore(actor.getId(), payload, registry));
            return new RestorePreview(id, payload.archiveId(), payload.fiscalYear(),
                (long) payload.offerings().size() + payload.linkedIncome().size()
                    + payload.expenses().size() + payload.budgets().size(), "VALIDATED");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void validateDependencies(FiscalArchivePayload payload) {
        List<String> missingMembers = payload.offerings().stream().map(Offering::getMemberId)
            .filter(java.util.Objects::nonNull).distinct().filter(id -> !members.existsById(id)).toList();
        if (!missingMembers.isEmpty()) {
            throw new IllegalStateException("Fiscal archive references a member that no longer exists.");
        }
        validateCodes(java.util.stream.Stream.concat(
                payload.offerings().stream().map(Offering::getFundCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.OFFERING_INCOME)
                    .map(Budget::getCategory)
            ).toList(),
            ReferenceDataType.OFFERING_FUND_CATEGORY, "fund/category");
        validateCodes(payload.offerings().stream().map(Offering::getPaymentMethod).toList(),
            ReferenceDataType.PAYMENT_METHOD, "payment method");
        validateCodes(java.util.stream.Stream.concat(
                payload.expenses().stream().map(FinancialTransaction::getCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.EXPENSE)
                    .map(Budget::getCategory)
            ).toList(),
            ReferenceDataType.FINANCIAL_CATEGORY, "financial category");
        validateCodes(java.util.stream.Stream.concat(
                payload.expenses().stream().map(FinancialTransaction::getSubCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.EXPENSE)
                    .map(Budget::getSubCategory)
            ).toList(),
            ReferenceDataType.FINANCIAL_SUB_CATEGORY, "financial sub-category");
    }

    private void validateCodes(List<String> codes, ReferenceDataType type, String label) {
        boolean missing = codes.stream().filter(java.util.Objects::nonNull).distinct()
            .anyMatch(code -> !references.existsByTypeAndCode(type, code));
        if (missing) {
            throw new IllegalStateException("Fiscal archive references a missing " + label + ".");
        }
    }

    public FiscalArchiveRegistry executeRestore(Member actor, String id, String confirmation) {
        requireAdmin(actor);
        StagedRestore restore = stagedRestores.get(id);
        if (restore == null || !restore.actorId.equals(actor.getId())) {
            throw new IllegalArgumentException("Fiscal restore operation was not found.");
        }
        if (!("RESTORE FISCAL YEAR " + restore.payload.fiscalYear()).equals(confirmation)) {
            throw new IllegalArgumentException("The fiscal restore confirmation phrase does not match.");
        }
        FiscalArchivePayload payload = restore.payload;
        String mutationId = "fiscal-restore:" + id;
        operationStore.beginExternalMutation(mutationId);
        try {
            offerings.saveAll(payload.offerings());
            transactions.saveAll(java.util.stream.Stream.concat(
                payload.linkedIncome().stream(), payload.expenses().stream()
            ).toList());
            budgets.saveAll(payload.budgets());
            restore.registry.setStatus(FiscalArchiveStatus.RESTORED);
            restore.registry.setRestoredAt(clock.instant());
            stagedRestores.remove(id);
            return registries.save(restore.registry);
        } catch (RuntimeException | Error exception) {
            budgets.deleteAllById(payload.budgets().stream().map(Budget::getId).toList());
            transactions.deleteAllById(java.util.stream.Stream.concat(
                payload.linkedIncome().stream(), payload.expenses().stream()
            ).map(FinancialTransaction::getId).toList());
            offerings.deleteAllById(payload.offerings().stream().map(Offering::getId).toList());
            throw exception;
        } finally {
            operationStore.endExternalMutation(mutationId);
        }
    }

    private FiscalArchivePayload select(int fiscalYear, String archiveId) {
        DateRange range = range(fiscalYear);
        List<Offering> selectedOfferings = offerings.findAllByOfferingDateBetween(range.start(), range.end());
        List<String> offeringIds = selectedOfferings.stream().map(Offering::getId).toList();
        List<FinancialTransaction> linkedIncome = transactions.findOfferingTransactionsBySourceIds(offeringIds);
        List<FinancialTransaction> expenses = transactions.findManualExpensesByTransactionDateBetween(
            range.start(), range.end()
        );
        List<Budget> selectedBudgets = budgets.findAllByFiscalYear(fiscalYear);
        return new FiscalArchivePayload(
            archiveId, fiscalYear, range.start(), range.end(), selectedOfferings,
            linkedIncome, expenses, selectedBudgets
        );
    }

    private FiscalArchiveRegistry registry(Member actor, FiscalArchivePayload payload, String checksum) {
        FiscalArchiveRegistry registry = new FiscalArchiveRegistry();
        registry.setArchiveId(payload.archiveId());
        registry.setChecksum(checksum);
        registry.setFiscalYear(payload.fiscalYear());
        registry.setStartDate(payload.startDate());
        registry.setEndDate(payload.endDate());
        registry.setStatus(FiscalArchiveStatus.STAGED);
        registry.setOfferingCount(payload.offerings().size());
        registry.setLinkedIncomeCount(payload.linkedIncome().size());
        registry.setExpenseCount(payload.expenses().size());
        registry.setBudgetCount(payload.budgets().size());
        registry.setMemberIds(payload.offerings().stream().map(Offering::getMemberId)
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        registry.setFundCategories(java.util.stream.Stream.concat(
                payload.offerings().stream().map(Offering::getFundCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.OFFERING_INCOME)
                    .map(Budget::getCategory)
            )
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        registry.setPaymentMethods(payload.offerings().stream().map(Offering::getPaymentMethod)
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        registry.setCategories(java.util.stream.Stream.concat(
                payload.expenses().stream().map(FinancialTransaction::getCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.EXPENSE)
                    .map(Budget::getCategory)
            )
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        registry.setSubCategories(java.util.stream.Stream.concat(
                payload.expenses().stream().map(FinancialTransaction::getSubCategory),
                payload.budgets().stream()
                    .filter(value -> value.getBudgetType() == com.church.operation.util.BudgetType.EXPENSE)
                    .map(Budget::getSubCategory)
            )
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        registry.setActorId(actor.getId());
        registry.setCreatedAt(clock.instant());
        return registry;
    }

    private StagedArchive requireStaged(Member actor, String archiveId) {
        StagedArchive staged = stagedArchives.get(archiveId);
        if (staged == null || !staged.actorId.equals(actor.getId())) {
            throw new IllegalArgumentException("Fiscal archive operation was not found.");
        }
        return staged;
    }

    private void requireAdmin(Member actor) {
        if (actor == null || actor.getRoles() == null || !actor.getRoles().contains(Role.ADMIN)) {
            throw new SecurityException("Administrator access is required for fiscal archives.");
        }
    }

    private void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Archive password is required.");
        }
    }

    DateRange range(int fiscalYear) {
        int startMonth = fiscalYearProperties.startMonth();
        LocalDate start = LocalDate.of(fiscalYear, Month.of(startMonth), 1);
        return new DateRange(start, start.plusYears(1).minusDays(1));
    }

    record DateRange(LocalDate start, LocalDate end) {
    }

    private static final class StagedArchive {
        private final String actorId;
        private final FiscalArchivePayload payload;
        private final FiscalArchiveRegistry registry;
        private boolean downloaded;

        private StagedArchive(String actorId, FiscalArchivePayload payload, FiscalArchiveRegistry registry) {
            this.actorId = actorId;
            this.payload = payload;
            this.registry = registry;
        }
    }

    private record StagedRestore(String actorId, FiscalArchivePayload payload, FiscalArchiveRegistry registry) {
    }

    private record BudgetKey(int fiscalYear, com.church.operation.util.BudgetType type, String category,
                             String subCategory) {
        private static BudgetKey from(Budget budget) {
            return new BudgetKey(
                budget.getFiscalYear(), budget.getBudgetType(), budget.getCategory(), budget.getSubCategory()
            );
        }
    }

    public record RestorePreview(String id, String archiveId, int fiscalYear, long totalRecordCount, String status) {
    }

    public record DownloadArtifact(Path path, String filename, String archiveId, Runnable afterDownload)
        implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                afterDownload.run();
            } finally {
                Files.deleteIfExists(path);
            }
        }
    }
}
