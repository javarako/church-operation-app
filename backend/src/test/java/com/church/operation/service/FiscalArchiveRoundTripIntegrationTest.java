package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "church.fiscal-year.start-month=4")
class FiscalArchiveRoundTripIntegrationTest {
    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:7.0.17"));

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGODB.getConnectionString() + "/fiscal_round_trip");
    }

    @Autowired FiscalArchiveService service;
    @Autowired OfferingRepository offerings;
    @Autowired FinancialTransactionRepository transactions;
    @Autowired BudgetRepository budgets;
    @Autowired MemberRepository members;
    @Autowired ReferenceDataRepository references;
    @TempDir Path tempDirectory;

    @Test
    void archivesCleansAndMergeRestoresOnlyTheSelectedNonJanuaryFiscalYear() throws Exception {
        Member admin = member("roundtrip-admin", "roundtrip-admin@example.test", Set.of(Role.ADMIN));
        Member donor = member("roundtrip-donor", "roundtrip-donor@example.test", Set.of(Role.MEMBER));
        members.save(admin);
        members.save(donor);
        reference(ReferenceDataType.OFFERING_FUND_CATEGORY, "ROUNDTRIP_FUND");
        reference(ReferenceDataType.PAYMENT_METHOD, "ROUNDTRIP_CASH");
        reference(ReferenceDataType.FINANCIAL_CATEGORY, "ROUNDTRIP_EXPENSE");
        reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "ROUNDTRIP_SUPPLIES");

        Offering before = offering("roundtrip-before", donor.getId(), LocalDate.of(2026, 3, 31), false);
        Offering inside = offering("roundtrip-inside", donor.getId(), LocalDate.of(2026, 4, 1), false);
        Offering deletedInside = offering("roundtrip-deleted", donor.getId(), LocalDate.of(2027, 3, 31), true);
        Offering after = offering("roundtrip-after", donor.getId(), LocalDate.of(2027, 4, 1), false);
        offerings.saveAll(java.util.List.of(before, inside, deletedInside, after));
        FinancialTransaction income = income("roundtrip-income", inside.getId());
        FinancialTransaction deletedIncome = income("roundtrip-deleted-income", deletedInside.getId());
        deletedIncome.setDeleted(true);
        FinancialTransaction expense = expense("roundtrip-expense", LocalDate.of(2026, 8, 1));
        FinancialTransaction outsideExpense = expense("roundtrip-outside-expense", LocalDate.of(2027, 4, 1));
        transactions.saveAll(java.util.List.of(income, deletedIncome, expense, outsideExpense));
        Budget budget = budget("roundtrip-budget", 2026);
        Budget outsideBudget = budget("roundtrip-outside-budget", 2027);
        budgets.saveAll(java.util.List.of(budget, outsideBudget));

        char[] password = "fiscal round trip password".toCharArray();
        FiscalArchiveService.DownloadArtifact download = service.createArchive(admin, 2026, password);
        Path archive = tempDirectory.resolve("fiscal-2026.zip");
        Files.copy(download.path(), archive);
        String archiveId = download.archiveId();
        download.close();
        service.clean(admin, archiveId, "CLEAN FISCAL YEAR 2026");

        assertThat(offerings.existsById(before.getId())).isTrue();
        assertThat(offerings.existsById(after.getId())).isTrue();
        assertThat(offerings.existsById(inside.getId())).isFalse();
        assertThat(offerings.existsById(deletedInside.getId())).isFalse();
        assertThat(transactions.existsById(outsideExpense.getId())).isTrue();
        assertThat(transactions.existsById(expense.getId())).isFalse();
        assertThat(budgets.existsById(outsideBudget.getId())).isTrue();
        assertThat(budgets.existsById(budget.getId())).isFalse();

        FiscalArchiveService.RestorePreview restore = service.validateRestore(
            admin, archive, "fiscal round trip password".toCharArray()
        );
        service.executeRestore(admin, restore.id(), "RESTORE FISCAL YEAR 2026");

        assertThat(offerings.findAll()).extracting(Offering::getId)
            .contains(before.getId(), inside.getId(), deletedInside.getId(), after.getId());
        assertThat(transactions.findAll()).extracting(FinancialTransaction::getId)
            .contains(income.getId(), deletedIncome.getId(), expense.getId(), outsideExpense.getId());
        assertThat(budgets.findAll()).extracting(Budget::getId).contains(budget.getId(), outsideBudget.getId());
    }

    private Member member(String id, String email, Set<Role> roles) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(email);
        member.setRoles(roles);
        return member;
    }

    private void reference(ReferenceDataType type, String code) {
        ReferenceData value = new ReferenceData();
        value.setType(type);
        value.setCode(code);
        value.setLabel(code);
        references.save(value);
    }

    private Offering offering(String id, String memberId, LocalDate date, boolean deleted) {
        Offering value = new Offering();
        value.setId(id);
        value.setGivingType(GivingType.MEMBER);
        value.setMemberId(memberId);
        value.setOfferingDate(date);
        value.setOfferingSunday(date);
        value.setFundCategory("ROUNDTRIP_FUND");
        value.setPaymentMethod("ROUNDTRIP_CASH");
        value.setAmount(new BigDecimal("25.00"));
        value.setDeleted(deleted);
        return value;
    }

    private FinancialTransaction income(String id, String offeringId) {
        FinancialTransaction value = new FinancialTransaction();
        value.setId(id);
        value.setType(FinancialTransactionType.INCOME);
        value.setSourceType(FinancialSourceType.OFFERING);
        value.setSourceId(offeringId);
        value.setTransactionDate(LocalDate.of(2026, 7, 1));
        value.setAmount(new BigDecimal("25.00"));
        return value;
    }

    private FinancialTransaction expense(String id, LocalDate date) {
        FinancialTransaction value = new FinancialTransaction();
        value.setId(id);
        value.setType(FinancialTransactionType.EXPENSE);
        value.setSourceType(FinancialSourceType.MANUAL);
        value.setTransactionDate(date);
        value.setCategory("ROUNDTRIP_EXPENSE");
        value.setSubCategory("ROUNDTRIP_SUPPLIES");
        value.setAmount(new BigDecimal("10.00"));
        return value;
    }

    private Budget budget(String id, int year) {
        Budget value = new Budget();
        value.setId(id);
        value.setFiscalYear(year);
        value.setBudgetType(BudgetType.EXPENSE);
        value.setCategory("ROUNDTRIP_EXPENSE");
        value.setSubCategory("ROUNDTRIP_SUPPLIES");
        value.setBudget(new BigDecimal("100.00"));
        return value;
    }
}
