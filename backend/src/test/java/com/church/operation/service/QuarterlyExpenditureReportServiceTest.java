package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.QuarterlyFinancialGroup;
import com.church.operation.dto.QuarterlyFinancialReport;
import com.church.operation.dto.QuarterlyFinancialRow;
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
class QuarterlyExpenditureReportServiceTest {
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void buildsCalendarQuarterInsideTheContainingFiscalYear() {
        QuarterlyFinancialReport report = service(7).build(member(Role.VIEWER), 2026, 2);

        assertThat(report.quarterStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(report.quarterEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(report.fiscalBudgetYear()).isEqualTo(2025);
        assertThat(report.sheetName()).isEqualTo("Expenditure");
        assertThat(report.titleSuffix()).isEqualTo("지출");
        assertThat(report.specialRowLabel()).isEqualTo("CONTINGENCY");
    }

    @Test
    void permitsGeneralReportRolesAndRejectsMembersAndInvalidPeriods() {
        for (Role role : List.of(Role.ADMIN, Role.TREASURER, Role.PASTOR, Role.VIEWER)) {
            assertThat(service(1).build(member(role), 2026, 1).quarter()).isEqualTo(1);
        }

        QuarterlyExpenditureReportService service = service(1);
        assertThatThrownBy(() -> service.build(member(Role.MEMBER), 2026, 1))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("permission");
        assertThatThrownBy(() -> service.build(member(Role.VIEWER), 1999, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.build(member(Role.VIEWER), 2026, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.build(member(Role.VIEWER), 2026, 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregatesExpenseBudgetsAndTransactionsWithContingencySeparate() {
        Budget income = budget(BudgetType.OFFERING_INCOME, "GENERAL", "TITHE", "9999");
        when(budgetRepository.findActiveByFiscalYear(2025)).thenReturn(List.of(
            income,
            budget(BudgetType.EXPENSE, "ADMIN", "OFFICE", "12000"),
            budget(BudgetType.EXPENSE, "MISSION", "SUPPORT", "2400"),
            budget(BudgetType.EXPENSE, "CONTINGENCY", "GENERAL", "2000"),
            budget(BudgetType.EXPENSE, "Contingency", "EMERGENCY", "500")
        ));
        when(transactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2025, 7, 1),
            LocalDate.of(2026, 6, 30)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.EXPENSE, "2025-08-03", "ADMIN", "OFFICE", "300"),
            transaction(FinancialTransactionType.EXPENSE, "2026-04-05", "ADMIN", "OFFICE", "100"),
            transaction(FinancialTransactionType.EXPENSE, "2026-05-03", "ADMIN", "OFFICE", "200"),
            transaction(FinancialTransactionType.EXPENSE, "2026-06-07", "PROPERTY", "REPAIR", "50"),
            transaction(FinancialTransactionType.EXPENSE, "2026-06-14", "Contingency", "EMERGENCY", "999"),
            transaction(FinancialTransactionType.INCOME, "2026-06-21", "ADMIN", "OFFICE", "700")
        ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.FINANCIAL_CATEGORY))
            .thenReturn(List.of(
                reference(ReferenceDataType.FINANCIAL_CATEGORY, "PROPERTY", "Property", null, 1, false),
                reference(ReferenceDataType.FINANCIAL_CATEGORY, "ADMIN", "Administration", null, 2, true),
                reference(ReferenceDataType.FINANCIAL_CATEGORY, "MISSION", "Mission", null, 3, true),
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
            reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "REPAIR", "Repairs", "PROPERTY", 1, false),
            reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "OFFICE", "Office", "ADMIN", 1, true),
            reference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPORT", "Support", "MISSION", 1, true)
        ));

        QuarterlyFinancialReport report = service(7).build(member(Role.TREASURER), 2026, 2);

        assertThat(report.groups()).extracting(QuarterlyFinancialGroup::groupCode)
            .containsExactly("PROPERTY", "ADMIN", "MISSION");
        assertThat(report.groups()).noneMatch(group ->
            group.groupCode().equalsIgnoreCase("CONTINGENCY")
        );
        QuarterlyFinancialRow office = report.groups().get(1).rows().getFirst();
        assertThat(office.budget()).isEqualByComparingTo("12000");
        assertThat(office.monthlyActuals()).containsExactly(
            new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO
        );
        assertThat(office.cumulativeActual()).isEqualByComparingTo("600");
        QuarterlyFinancialRow repair = report.groups().getFirst().rows().getFirst();
        assertThat(repair.itemLabel()).isEqualTo("Repairs");
        assertThat(repair.budget()).isEqualByComparingTo("0");
        assertThat(repair.cumulativeActual()).isEqualByComparingTo("50");
        assertThat(report.specialBudget()).isEqualByComparingTo("2500");
        assertThat(report.specialMonthlyActuals()).containsExactly(
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999")
        );
        assertThat(report.specialCumulativeActual()).isEqualByComparingTo("999");
        assertThat(report.specialRowLabel()).isEqualTo("Emergency Reserve");

        verify(budgetRepository).findActiveByFiscalYear(2025);
        verify(transactionRepository).findActiveByTransactionDateBetween(
            LocalDate.of(2025, 7, 1),
            LocalDate.of(2026, 6, 30)
        );
    }

    @Test
    void fallsBackToCodesAndIgnoresInvalidAmountsAndMissingKeys() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.EXPENSE, "UNKNOWN", "UNKNOWN_ITEM", null),
            budget(BudgetType.EXPENSE, null, "ORPHAN", "10")
        ));
        when(transactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.EXPENSE, "2026-01-04", "UNKNOWN", "UNKNOWN_ITEM", null),
            transaction(FinancialTransactionType.EXPENSE, "2026-02-01", "UNKNOWN", "UNKNOWN_ITEM", "-5"),
            transaction(FinancialTransactionType.EXPENSE, "2026-02-08", "UNKNOWN", null, "15")
        ));

        QuarterlyFinancialReport report = service(1).build(member(Role.ADMIN), 2026, 1);

        assertThat(report.groups()).hasSize(1);
        assertThat(report.groups().getFirst().groupLabel()).isEqualTo("UNKNOWN");
        assertThat(report.groups().getFirst().rows()).singleElement()
            .satisfies(row -> {
                assertThat(row.itemLabel()).isEqualTo("UNKNOWN_ITEM");
                assertThat(row.budget()).isEqualByComparingTo("0");
                assertThat(row.cumulativeActual()).isEqualByComparingTo("0");
            });
    }

    private QuarterlyExpenditureReportService service(int fiscalStartMonth) {
        return new QuarterlyExpenditureReportService(
            budgetRepository,
            transactionRepository,
            referenceDataRepository,
            new FiscalYearProperties(fiscalStartMonth)
        );
    }

    private Member member(Role role) {
        Member member = new Member();
        member.setId(role.name().toLowerCase());
        member.setRoles(Set.of(role));
        return member;
    }

    private Budget budget(
        BudgetType type,
        String categoryCode,
        String subCategoryCode,
        String amount
    ) {
        Budget budget = new Budget();
        budget.setFiscalYear(2025);
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
