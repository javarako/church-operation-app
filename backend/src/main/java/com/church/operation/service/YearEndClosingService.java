package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.YearEndClosingReportStatus;
import com.church.operation.dto.YearEndClosingRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class YearEndClosingService {
    private final YearEndClosingRepository repository;
    private final YearEndSnapshotStore snapshotStore;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final FiscalYearProperties fiscalYearProperties;
    private final YearlyOfferingReportService offeringReportService;
    private final YearlyExpenditureReportService expenditureReportService;
    private final YearlyFinancialExcelService excelService;
    private final SystemAuditService audit;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public YearEndClosingService(
        YearEndClosingRepository repository,
        YearEndSnapshotStore snapshotStore,
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        FiscalYearProperties fiscalYearProperties,
        YearlyOfferingReportService offeringReportService,
        YearlyExpenditureReportService expenditureReportService,
        YearlyFinancialExcelService excelService,
        SystemAuditService audit,
        MongoTemplate mongoTemplate
    ) {
        this(
            repository,
            snapshotStore,
            memberRepository,
            passwordEncoder,
            fiscalYearProperties,
            offeringReportService,
            expenditureReportService,
            excelService,
            audit,
            mongoTemplate,
            Clock.systemDefaultZone()
        );
    }

    YearEndClosingService(
        YearEndClosingRepository repository,
        YearEndSnapshotStore snapshotStore,
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        FiscalYearProperties fiscalYearProperties,
        YearlyOfferingReportService offeringReportService,
        YearlyExpenditureReportService expenditureReportService,
        YearlyFinancialExcelService excelService,
        SystemAuditService audit,
        MongoTemplate mongoTemplate,
        Clock clock
    ) {
        this.repository = repository;
        this.snapshotStore = snapshotStore;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.fiscalYearProperties = fiscalYearProperties;
        this.offeringReportService = offeringReportService;
        this.expenditureReportService = expenditureReportService;
        this.excelService = excelService;
        this.audit = audit;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public YearEndClosingStatusResponse status(Member actor, int fiscalYear) {
        requireReportAccess(actor);
        YearlyFinancialPeriod period = period(fiscalYear);
        return new YearEndClosingStatusResponse(
            fiscalYear,
            period.fiscalEnd(),
            LocalDate.now(clock).isAfter(period.fiscalEnd()),
            latestStatus(fiscalYear, YearEndReportType.OFFERING),
            latestStatus(fiscalYear, YearEndReportType.EXPENDITURE)
        );
    }

    public YearEndClosingReportStatus close(
        Member actor,
        YearEndReportType reportType,
        YearEndClosingRequest request
    ) {
        Map<String, Object> failureMetadata = baseMetadata(request.fiscalYear(), reportType);
        try {
            Member current = verifyLifecycleActor(actor, request.currentPassword());
            YearEndClosing closing = closeOperation(current, reportType, request.fiscalYear());
            audit.recordSuccess(current, SystemAuditOperation.YEAR_END_CLOSE, auditMetadata(closing));
            return toStatus(closing);
        } catch (RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.YEAR_END_CLOSE, failureMetadata, exception);
            throw exception;
        }
    }

    public YearEndClosingReportStatus reopen(
        Member actor,
        YearEndReportType reportType,
        YearEndClosingRequest request
    ) {
        Map<String, Object> failureMetadata = baseMetadata(request.fiscalYear(), reportType);
        try {
            Member current = verifyLifecycleActor(actor, request.currentPassword());
            period(request.fiscalYear());
            Query query = Query.query(Criteria.where("fiscalYear").is(request.fiscalYear())
                .and("reportType").is(reportType)
                .and("active").is(true)
                .and("status").is(YearEndClosingStatus.CLOSED));
            Update update = new Update()
                .set("status", YearEndClosingStatus.REOPENED)
                .set("active", false)
                .unset("activeKey")
                .set("reopenedByMemberId", current.getId())
                .set("reopenedByEmail", current.getPrimaryEmail())
                .set("reopenedAt", Instant.now(clock));
            YearEndClosing saved = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                YearEndClosing.class
            );
            if (saved == null) {
                throw new YearEndClosingConflictException(
                    "This yearly report is not currently closed."
                );
            }
            audit.recordSuccess(current, SystemAuditOperation.YEAR_END_REOPEN, auditMetadata(saved));
            return toStatus(saved);
        } catch (RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.YEAR_END_REOPEN, failureMetadata, exception);
            throw exception;
        }
    }

    public YearlyWorkbookDownload download(
        Member actor,
        YearEndReportType reportType,
        int fiscalYear
    ) {
        requireReportAccess(actor);
        period(fiscalYear);
        Optional<YearEndClosing> active = repository
            .findByFiscalYearAndReportTypeAndActiveTrue(fiscalYear, reportType);
        if (active.isPresent()) {
            YearEndClosing closing = active.get();
            return new YearlyWorkbookDownload(snapshotStore.load(closing), closing.getFilename());
        }

        YearlyWorkbookLifecycle lifecycle = repository
            .findFirstByFiscalYearAndReportTypeOrderByVersionDesc(fiscalYear, reportType)
            .filter(closing -> closing.getStatus() == YearEndClosingStatus.REOPENED)
            .map(closing -> YearlyWorkbookLifecycle.reopened(closing.getReopenedAt()))
            .orElseGet(YearlyWorkbookLifecycle::notClosed);
        YearlyFinancialReport report = buildReport(actor, reportType, fiscalYear);
        return new YearlyWorkbookDownload(
            excelService.render(report, lifecycle),
            filenamePrefix(reportType) + "-" + fiscalYear + "-draft.xlsx"
        );
    }

    private YearEndClosing closeOperation(
        Member current,
        YearEndReportType reportType,
        int fiscalYear
    ) {
        YearlyFinancialPeriod period = period(fiscalYear);
        if (!LocalDate.now(clock).isAfter(period.fiscalEnd())) {
            throw new IllegalArgumentException(
                "Year-end closing is available after " + period.fiscalEnd() + "."
            );
        }
        if (repository.findByFiscalYearAndReportTypeAndActiveTrue(fiscalYear, reportType).isPresent()) {
            throw new YearEndClosingConflictException("This yearly report is already closed.");
        }

        int version = repository
            .findFirstByFiscalYearAndReportTypeOrderByVersionDesc(fiscalYear, reportType)
            .map(closing -> closing.getVersion() + 1)
            .orElse(1);
        Instant now = Instant.now(clock);
        String filename = filenamePrefix(reportType) + "-" + fiscalYear + "-closed-v" + version + ".xlsx";
        YearlyFinancialReport report = buildReport(current, reportType, fiscalYear);
        byte[] workbook = excelService.render(report, YearlyWorkbookLifecycle.closed(now, version));
        YearEndSnapshotStore.StoredSnapshot snapshot = snapshotStore.store(
            workbook,
            filename,
            fiscalYear,
            reportType,
            version
        );

        try {
            YearEndClosing closing = new YearEndClosing();
            closing.setFiscalYear(fiscalYear);
            closing.setReportType(reportType);
            closing.setVersion(version);
            closing.setStatus(YearEndClosingStatus.CLOSED);
            closing.setActive(true);
            closing.setActiveKey(YearEndClosing.activeKey(fiscalYear, reportType));
            closing.setGridFsFileId(snapshot.gridFsFileId());
            closing.setFilename(filename);
            closing.setContentType(YearEndSnapshotStore.EXCEL_CONTENT_TYPE);
            closing.setFileSize(snapshot.fileSize());
            closing.setChecksum(snapshot.checksum());
            closing.setClosedByMemberId(current.getId());
            closing.setClosedByEmail(current.getPrimaryEmail());
            closing.setClosedAt(now);
            return repository.save(closing);
        } catch (RuntimeException exception) {
            deleteAfterFailure(snapshot.gridFsFileId(), exception);
            if (exception instanceof DuplicateKeyException) {
                throw new YearEndClosingConflictException(
                    "This yearly report was closed by another request.",
                    exception
                );
            }
            throw exception;
        }
    }

    private Member verifyLifecycleActor(Member actor, String currentPassword) {
        if (actor == null || actor.getId() == null) {
            throw new SecurityException("You do not have permission to close yearly reports.");
        }
        Member current = memberRepository.findById(actor.getId())
            .orElseThrow(() -> new SecurityException(
                "Your account is not permitted to close yearly reports."
            ));
        if (!hasFinanceRole(current)) {
            throw new SecurityException("You do not have permission to close yearly reports.");
        }
        if (!current.isActive() || current.isLocked()) {
            throw new SecurityException("Your account is not permitted to close yearly reports.");
        }
        if (!passwordEncoder.matches(currentPassword, current.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        return current;
    }

    private YearEndClosingReportStatus latestStatus(int fiscalYear, YearEndReportType reportType) {
        return repository.findFirstByFiscalYearAndReportTypeOrderByVersionDesc(fiscalYear, reportType)
            .map(this::toStatus)
            .orElseGet(() -> new YearEndClosingReportStatus(
                reportType,
                YearEndClosingStatus.NOT_CLOSED,
                null,
                null
            ));
    }

    private YearEndClosingReportStatus toStatus(YearEndClosing closing) {
        Instant eventAt = closing.getStatus() == YearEndClosingStatus.CLOSED
            ? closing.getClosedAt()
            : closing.getReopenedAt();
        return new YearEndClosingReportStatus(
            closing.getReportType(),
            closing.getStatus(),
            closing.getVersion(),
            eventAt
        );
    }

    private YearlyFinancialReport buildReport(
        Member actor,
        YearEndReportType reportType,
        int fiscalYear
    ) {
        return reportType == YearEndReportType.OFFERING
            ? offeringReportService.build(actor, fiscalYear)
            : expenditureReportService.build(actor, fiscalYear);
    }

    private YearlyFinancialPeriod period(int fiscalYear) {
        return YearlyFinancialPeriod.from(fiscalYear, fiscalYearProperties.startMonth());
    }

    private void requireReportAccess(Member actor) {
        if (actor != null && actor.getRoles() != null
            && actor.getRoles().stream().anyMatch(this::isReportRole)) {
            return;
        }
        throw new SecurityException("You do not have permission to view reports.");
    }

    private boolean isReportRole(Role role) {
        return role == Role.ADMIN
            || role == Role.TREASURER
            || role == Role.PASTOR
            || role == Role.VIEWER;
    }

    private boolean hasFinanceRole(Member member) {
        return member.getRoles() != null
            && (member.getRoles().contains(Role.ADMIN) || member.getRoles().contains(Role.TREASURER));
    }

    private String filenamePrefix(YearEndReportType reportType) {
        return reportType == YearEndReportType.OFFERING
            ? "yearly-offerings"
            : "yearly-expenditures";
    }

    private Map<String, Object> baseMetadata(int fiscalYear, YearEndReportType reportType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fiscalYear", fiscalYear);
        metadata.put("reportType", reportType.name());
        return metadata;
    }

    private Map<String, Object> auditMetadata(YearEndClosing closing) {
        Map<String, Object> metadata = baseMetadata(closing.getFiscalYear(), closing.getReportType());
        metadata.put("version", closing.getVersion());
        metadata.put("closingId", closing.getId());
        metadata.put("gridFsFileId", closing.getGridFsFileId());
        metadata.put("checksum", closing.getChecksum());
        metadata.put("fileSize", closing.getFileSize());
        return metadata;
    }

    private void deleteAfterFailure(String gridFsFileId, RuntimeException primaryFailure) {
        try {
            snapshotStore.delete(gridFsFileId);
        } catch (RuntimeException cleanupFailure) {
            cleanupFailure.addSuppressed(primaryFailure);
            throw new IllegalStateException("Year-end snapshot cleanup failed.", cleanupFailure);
        }
    }
}
