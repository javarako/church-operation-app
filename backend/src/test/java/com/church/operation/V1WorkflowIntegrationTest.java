package com.church.operation;

import com.church.operation.dto.MemberImageContent;
import com.church.operation.entity.Address;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.repo.SystemAuditEventRepository;
import com.church.operation.repo.TaxReceiptRepository;
import com.church.operation.service.FiscalArchiveService;
import com.church.operation.service.MemberDeletionService;
import com.church.operation.service.MemberImageService;
import com.church.operation.service.MongoDatabaseExportService;
import com.church.operation.service.MongoDatabaseImportService;
import com.church.operation.service.ReferenceDataDeletionService;
import com.church.operation.service.TaxReceiptService;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.GivingType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
    "church.fiscal-year.start-month=4",
    "church.information.name=Integration Church",
    "church.information.address=1 Test Street, Toronto, Ontario",
    "church.information.treasurer-name=Test Treasurer",
    "church.information.charity-registration-number=123456789RR0001",
    "church.information.receipt-issue-location=Toronto, Ontario",
    "church.information.website=https://church.example.test"
})
class V1WorkflowIntegrationTest {
    private static final byte[] FACE_IMAGE = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02, 0x03
    };
    private static final String THANK_YOU = "Thank you for your faithful and generous support.";

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:7.0.17"));

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGODB.getConnectionString() + "/v1_workflow");
    }

    @Autowired MemberRepository members;
    @Autowired ReferenceDataRepository references;
    @Autowired OfferingRepository offerings;
    @Autowired FinancialTransactionRepository transactions;
    @Autowired BudgetRepository budgets;
    @Autowired TaxReceiptRepository receipts;
    @Autowired SystemAuditEventRepository auditEvents;
    @Autowired MemberImageService memberImages;
    @Autowired TaxReceiptService taxReceipts;
    @Autowired FiscalArchiveService fiscalArchives;
    @Autowired MemberDeletionService memberDeletion;
    @Autowired ReferenceDataDeletionService referenceDeletion;
    @Autowired MongoDatabaseExportService exporter;
    @Autowired MongoDatabaseImportService importer;
    @Autowired MongoTemplate mongoTemplate;
    @TempDir Path tempDirectory;

    @Test
    void completesTheV1DataLifecycleWithoutLosingMasterDataImagesOrReceiptSnapshots() throws Exception {
        Member admin = member("v1-admin", "admin@v1.test", "9000", "System Administrator", Set.of(Role.ADMIN));
        Member donor = member("v1-donor", "donor@v1.test", "1001", "Ada Donor", Set.of(Role.MEMBER));
        members.saveAll(List.of(admin, donor));
        ReferenceData fund = reference(ReferenceDataType.OFFERING_FUND_CATEGORY, "V1_GENERAL", null);
        reference(ReferenceDataType.PAYMENT_METHOD, "V1_CASH", null);
        reference(ReferenceDataType.FINANCIAL_CATEGORY, "V1_OFFICE", null);
        reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "V1_SUPPLIES", "V1_OFFICE");

        memberImages.store(admin, donor.getId(), new MockMultipartFile(
            "faceImage", "donor.png", "image/png", FACE_IMAGE
        ));
        Offering offering = offering(donor.getId());
        offerings.save(offering);
        FinancialTransaction linkedIncome = income(offering.getId());
        FinancialTransaction expense = expense();
        transactions.saveAll(List.of(linkedIncome, expense));
        budgets.save(budget());
        TaxReceipt receipt = taxReceipts.issue(admin, 2026, donor.getOfferingNumber(), THANK_YOU);
        MemberImageContent expectedImage = memberImages.load(admin, donor.getId());
        List<String> expectedMemberIndexes = indexNames("members");
        long expectedMembers = members.count();
        long expectedReferences = references.count();
        long expectedOfferings = offerings.count();
        long expectedTransactions = transactions.count();
        long expectedBudgets = budgets.count();
        long expectedReceipts = receipts.count();
        long expectedAuditEvents = auditEvents.count();

        Path fullBackup = tempDirectory.resolve("v1-full-backup.zip");
        exporter.exportFull(fullBackup, "v1 full backup password".toCharArray());

        FiscalArchiveService.DownloadArtifact download = fiscalArchives.createArchive(
            admin, 2026, "v1 fiscal password".toCharArray()
        );
        Path fiscalArchive = tempDirectory.resolve("v1-fiscal-2026.zip");
        Files.copy(download.path(), fiscalArchive);
        String archiveId = download.archiveId();
        download.close();
        fiscalArchives.clean(admin, archiveId, "CLEAN FISCAL YEAR 2026");

        assertThat(members.existsById(donor.getId())).isTrue();
        assertThat(references.findById(fund.getId())).isPresent();
        assertThat(receipts.findById(receipt.getId())).isPresent();
        assertThat(memberImages.load(admin, donor.getId()).bytes()).isEqualTo(FACE_IMAGE);
        assertThat(offerings.existsById(offering.getId())).isFalse();

        FiscalArchiveService.RestorePreview fiscalRestore = fiscalArchives.validateRestore(
            admin, fiscalArchive, "v1 fiscal password".toCharArray()
        );
        fiscalArchives.executeRestore(admin, fiscalRestore.id(), "RESTORE FISCAL YEAR 2026");

        assertThatThrownBy(() -> memberDeletion.delete(admin, donor.getId()))
            .isInstanceOf(DeletionBlockedException.class);
        assertThatThrownBy(() -> referenceDeletion.delete(admin, fund.getId()))
            .isInstanceOf(DeletionBlockedException.class);

        offerings.deleteAll();
        transactions.deleteAll();
        budgets.deleteAll();
        receipts.deleteAll();
        references.deleteAll();
        members.deleteAll();

        try (MongoDatabaseImportService.ValidatedArchive validated = importer.validateFull(
            fullBackup, "v1 full backup password".toCharArray()
        )) {
            importer.replaceAll(validated);
        }

        assertThat(members.count()).isEqualTo(expectedMembers);
        assertThat(references.count()).isEqualTo(expectedReferences);
        assertThat(offerings.count()).isEqualTo(expectedOfferings);
        assertThat(transactions.count()).isEqualTo(expectedTransactions);
        assertThat(budgets.count()).isEqualTo(expectedBudgets);
        assertThat(receipts.count()).isEqualTo(expectedReceipts);
        assertThat(auditEvents.count()).isEqualTo(expectedAuditEvents);
        assertThat(indexNames("members")).containsExactlyElementsOf(expectedMemberIndexes);
        assertThat(memberImages.load(members.findById(admin.getId()).orElseThrow(), donor.getId()).bytes())
            .isEqualTo(expectedImage.bytes());
        TaxReceipt restoredReceipt = receipts.findById(receipt.getId()).orElseThrow();
        assertThat(restoredReceipt.getReceiptNumber()).isEqualTo(receipt.getReceiptNumber());
        assertThat(restoredReceipt.getDonorName()).isEqualTo("Ada Donor");
        assertThat(restoredReceipt.getGiftAmount()).isEqualByComparingTo("125.00");
        assertThat(restoredReceipt.getSourceOfferingIds()).containsExactly(offering.getId());
    }

    private List<String> indexNames(String collection) {
        return mongoTemplate.getCollection(collection).listIndexes()
            .map(document -> document.getString("name"))
            .into(new java.util.ArrayList<>());
    }

    private Member member(String id, String email, String offeringNumber, String name, Set<Role> roles) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(email);
        member.setOfferingNumber(offeringNumber);
        member.setDisplayName(name);
        member.setRoles(roles);
        member.setActive(true);
        member.setLocked(false);
        member.setMailingAddress(new Address("100 Main Street", null, "Toronto", "Ontario", "M1M 1M1", "Canada"));
        return member;
    }

    private ReferenceData reference(ReferenceDataType type, String code, String parentCode) {
        ReferenceData value = new ReferenceData();
        value.setType(type);
        value.setCode(code);
        value.setLabel(code);
        value.setParentCode(parentCode);
        return references.save(value);
    }

    private Offering offering(String memberId) {
        Offering value = new Offering();
        value.setId("v1-offering");
        value.setGivingType(GivingType.MEMBER);
        value.setMemberId(memberId);
        value.setOfferingDate(LocalDate.of(2026, 7, 5));
        value.setOfferingSunday(LocalDate.of(2026, 7, 5));
        value.setFundCategory("V1_GENERAL");
        value.setPaymentMethod("V1_CASH");
        value.setAmount(new BigDecimal("125.00"));
        return value;
    }

    private FinancialTransaction income(String offeringId) {
        FinancialTransaction value = new FinancialTransaction();
        value.setId("v1-income");
        value.setType(FinancialTransactionType.INCOME);
        value.setSourceType(FinancialSourceType.OFFERING);
        value.setSourceId(offeringId);
        value.setTransactionDate(LocalDate.of(2026, 7, 5));
        value.setAmount(new BigDecimal("125.00"));
        return value;
    }

    private FinancialTransaction expense() {
        FinancialTransaction value = new FinancialTransaction();
        value.setId("v1-expense");
        value.setType(FinancialTransactionType.EXPENSE);
        value.setSourceType(FinancialSourceType.MANUAL);
        value.setTransactionDate(LocalDate.of(2026, 8, 1));
        value.setCategory("V1_OFFICE");
        value.setSubCategory("V1_SUPPLIES");
        value.setAmount(new BigDecimal("40.00"));
        return value;
    }

    private Budget budget() {
        Budget value = new Budget();
        value.setId("v1-budget");
        value.setFiscalYear(2026);
        value.setBudgetType(BudgetType.EXPENSE);
        value.setCategory("V1_OFFICE");
        value.setSubCategory("V1_SUPPLIES");
        value.setBudget(new BigDecimal("500.00"));
        return value;
    }
}
