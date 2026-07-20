package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.YearlyFinancialGroup;
import com.church.operation.dto.YearlyFinancialReport;
import com.church.operation.dto.YearlyFinancialRow;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YearlyExpenditureReportServiceTest {
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void aggregatesExpenseYearsAndSeparatesContingency() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, BudgetType.EXPENSE, "ADMIN", "OFFICE", "12000"),
            budget(2026, BudgetType.EXPENSE, "CONTINGENCY", "GENERAL", "2000"),
            budget(2026, BudgetType.OFFERING_INCOME, "GENERAL", "TITHE", "9999")
        ));
        when(budgetRepository.findActiveByFiscalYear(2027)).thenReturn(List.of(
            budget(2027, BudgetType.EXPENSE, "ADMIN", "OFFICE", "12500"),
            budget(2027, BudgetType.EXPENSE, "PROPERTY", "REPAIR", "3000")
        ));
        when(transactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2027, 6, 30)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.EXPENSE, "2026-07-05", "ADMIN", "OFFICE", "250"),
            transaction(FinancialTransactionType.EXPENSE, "2027-06-27", "ADMIN", "OFFICE", "350"),
            transaction(FinancialTransactionType.EXPENSE, "2026-08-02", "contingency", "EMERGENCY", "150"),
            transaction(FinancialTransactionType.INCOME, "2026-09-06", "ADMIN", "OFFICE", "500")
        ));
        mockReferences();

        YearlyFinancialReport report = service(7).build(member(Role.ADMIN), 2026);

        assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(report.fiscalEnd()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(report.sheetName()).isEqualTo("Expenditure");
        assertThat(report.titleSuffix()).isEqualTo("지출 결산 및 예산안");
        assertThat(report.actualHeader()).isEqualTo("지출결산");
        assertThat(report.actualRatioHeader()).isEqualTo("지출대비");
        assertThat(report.specialRowLabel()).isEqualTo("Emergency Reserve");

        YearlyFinancialRow office = findRow(report, "OFFICE");
        assertThat(office.currentBudget()).isEqualByComparingTo("12000");
        assertThat(office.actual()).isEqualByComparingTo("600");
        assertThat(office.nextBudget()).isEqualByComparingTo("12500");
        assertThat(office.nextBudgetPresent()).isTrue();

        YearlyFinancialRow repair = findRow(report, "REPAIR");
        assertThat(repair.currentBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repair.actual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repair.nextBudget()).isEqualByComparingTo("3000");

        assertThat(report.specialCurrentBudget()).isEqualByComparingTo("2000");
        assertThat(report.specialActual()).isEqualByComparingTo("150");
        assertThat(report.specialNextBudgetPresent()).isFalse();
        assertThat(report.groups()).noneMatch(group -> group.groupCode().equalsIgnoreCase("CONTINGENCY"));

        verify(transactionRepository).findActiveByTransactionDateBetween(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2027, 6, 30)
        );
    }

    @Test
    void distinguishesMissingNextBudgetFromEnteredZeroAndUsesCodeFallbacks() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, BudgetType.EXPENSE, "ADMIN", "OFFICE", "12000"),
            budget(2026, BudgetType.EXPENSE, "MISSION", "SUPPORT", "2400")
        ));
        when(budgetRepository.findActiveByFiscalYear(2027)).thenReturn(List.of(
            budget(2027, BudgetType.EXPENSE, "MISSION", "SUPPORT", "0")
        ));

        YearlyFinancialReport report = service(1).build(member(Role.VIEWER), 2026);

        assertThat(findRow(report, "OFFICE").nextBudgetPresent()).isFalse();
        assertThat(findRow(report, "SUPPORT").nextBudgetPresent()).isTrue();
        assertThat(findRow(report, "SUPPORT").nextBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.groups()).extracting(YearlyFinancialGroup::groupLabel)
            .containsExactly("ADMIN", "MISSION");
        assertThat(findRow(report, "OFFICE").itemLabel()).isEqualTo("OFFICE");
        assertThat(report.specialRowLabel()).isEqualTo("CONTINGENCY");
    }

    @Test
    void ignoresMalformedAndNonPositiveRecordsAndRetainsInactiveReferenceLabels() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, BudgetType.EXPENSE, "PROPERTY", "REPAIR", null),
            budget(2026, BudgetType.EXPENSE, "", "ORPHAN", "25")
        ));
        when(transactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.EXPENSE, "2026-01-04", "PROPERTY", "REPAIR", null),
            transaction(FinancialTransactionType.EXPENSE, "2026-01-11", "PROPERTY", "REPAIR", "-1"),
            transaction(FinancialTransactionType.EXPENSE, "2026-01-18", "PROPERTY", null, "25"),
            transaction(FinancialTransactionType.EXPENSE, "2026-01-25", "PROPERTY", "REPAIR", "75")
        ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.FINANCIAL_CATEGORY))
            .thenReturn(List.of(reference(
                ReferenceDataType.FINANCIAL_CATEGORY, "PROPERTY", "Property", null, 1, false
            )));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY
        )).thenReturn(List.of(reference(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY, "REPAIR", "Repairs", "PROPERTY", 1, false
        )));

        YearlyFinancialReport report = service(1).build(member(Role.PASTOR), 2026);

        assertThat(report.groups()).singleElement().satisfies(group -> {
            assertThat(group.groupLabel()).isEqualTo("Property");
            assertThat(group.rows()).singleElement().satisfies(row -> {
                assertThat(row.itemLabel()).isEqualTo("Repairs");
                assertThat(row.actual()).isEqualByComparingTo("75");
            });
        });
    }

    @Test
    void permitsGeneralReportRolesAndRejectsInvalidAccessOrYear() {
        for (Role role : List.of(Role.ADMIN, Role.TREASURER, Role.PASTOR, Role.VIEWER)) {
            assertThat(service(1).build(member(role), 2026).fiscalYear()).isEqualTo(2026);
        }

        assertThatThrownBy(() -> service(1).build(member(Role.MEMBER), 2026))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("permission");
        assertThatThrownBy(() -> service(1).build(member(Role.VIEWER), 1999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A valid fiscal year is required.");
    }

    private YearlyExpenditureReportService service(int fiscalStartMonth) {
        return new YearlyExpenditureReportService(
            budgetRepository,
            transactionRepository,
            referenceDataRepository,
            new FiscalYearProperties(fiscalStartMonth)
        );
    }

    private YearlyFinancialRow findRow(YearlyFinancialReport report, String itemCode) {
        return report.groups().stream()
            .flatMap(group -> group.rows().stream())
            .filter(row -> row.itemCode().equals(itemCode))
            .findFirst()
            .orElseThrow();
    }

    private Member member(Role role) {
        Member member = new Member();
        member.setId(role.name().toLowerCase());
        member.setRoles(Set.of(role));
        return member;
    }

    private Budget budget(
        int fiscalYear,
        BudgetType type,
        String categoryCode,
        String subCategoryCode,
        String amount
    ) {
        Budget budget = new Budget();
        budget.setFiscalYear(fiscalYear);
        budget.setBudgetType(type);
        budget.setCategory(categoryCode);
        budget.setSubCategory(subCategoryCode);
        budget.setBudget(amount == null ? null : new BigDecimal(amount));
        return budget;
    }

    private FinancialTransaction transaction(
        FinancialTransactionType type,
        String date,
        String categoryCode,
        String subCategoryCode,
        String amount
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setType(type);
        transaction.setTransactionDate(LocalDate.parse(date));
        transaction.setCategory(categoryCode);
        transaction.setSubCategory(subCategoryCode);
        transaction.setAmount(amount == null ? null : new BigDecimal(amount));
        return transaction;
    }

    private void mockReferences() {
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.FINANCIAL_CATEGORY))
            .thenReturn(List.of(
                reference(ReferenceDataType.FINANCIAL_CATEGORY, "ADMIN", "Administration", null, 1, true),
                reference(ReferenceDataType.FINANCIAL_CATEGORY, "PROPERTY", "Property", null, 2, true),
                reference(
                    ReferenceDataType.FINANCIAL_CATEGORY,
                    "CONTINGENCY",
                    "Emergency Reserve",
                    null,
                    99,
                    true
                )
            ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY
        )).thenReturn(List.of(
            reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "OFFICE", "Office", "ADMIN", 1, true),
            reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "REPAIR", "Repairs", "PROPERTY", 1, true)
        ));
    }

    private ReferenceData reference(
        ReferenceDataType type,
        String code,
        String label,
        String parentCode,
        int sortOrder,
        boolean active
    ) {
        ReferenceData reference = new ReferenceData();
        reference.setType(type);
        reference.setCode(code);
        reference.setLabel(label);
        reference.setParentCode(parentCode);
        reference.setSortOrder(sortOrder);
        reference.setActive(active);
        return reference;
    }
}
