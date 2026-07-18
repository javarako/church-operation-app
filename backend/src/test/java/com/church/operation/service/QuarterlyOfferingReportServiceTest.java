package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.QuarterlyFinancialGroup;
import com.church.operation.dto.QuarterlyFinancialReport;
import com.church.operation.dto.QuarterlyFinancialRow;
import com.church.operation.entity.Budget;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarterlyOfferingReportServiceTest {
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void buildsCalendarQuarterWithinJanuaryFiscalYear() {
        QuarterlyFinancialReport report = service(1).build(member(Role.VIEWER), 2026, 2);

        assertThat(report.quarterStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(report.quarterEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(report.fiscalBudgetYear()).isEqualTo(2026);
        assertThat(report.months()).extracting(Object::toString)
            .containsExactly("2026-04", "2026-05", "2026-06");
    }

    @Test
    void selectsFiscalPeriodContainingQuarterEnd() {
        QuarterlyFinancialReport report = service(7).build(member(Role.PASTOR), 2026, 1);

        assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(report.fiscalBudgetYear()).isEqualTo(2025);
    }

    @Test
    void permitsAllGeneralReportRoles() {
        for (Role role : List.of(Role.ADMIN, Role.TREASURER, Role.PASTOR, Role.VIEWER)) {
            assertThat(service(1).build(member(role), 2026, 1).quarter()).isEqualTo(1);
        }
    }

    @Test
    void rejectsMemberOnlyAccessAndInvalidSelections() {
        QuarterlyOfferingReportService service = service(1);

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
    void aggregatesBudgetAndOfferingUnionBySundayWithCarryOverSeparate() {
        when(budgetRepository.findActiveByFiscalYear(2025)).thenReturn(List.of(
            budget("GENERAL", "TITHE", "12000"),
            budget("GENERAL", "MISSIONS", "2400"),
            budget("CARRY_OVER", null, "2500")
        ));
        when(offeringRepository
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 6, 30)
            )).thenReturn(List.of(
                offering("2025-08-03", "GENERAL", "TITHE", "300"),
                offering("2026-04-05", "GENERAL", "TITHE", "100"),
                offering("2026-05-03", "GENERAL", "TITHE", "200"),
                offering("2026-06-07", "BUILDING", "PROJECT", "50"),
                offering("2026-06-14", "Carry_Over", "LEGACY", "999")
            ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_FUND, "BUILDING", "Building Fund", null, 1, false),
                reference(ReferenceDataType.OFFERING_FUND, "GENERAL", "General Fund", null, 2, true)
            ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_CATEGORY))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_CATEGORY, "PROJECT", "Building Project", "BUILDING", 1, false),
                reference(ReferenceDataType.OFFERING_CATEGORY, "MISSIONS", "Missions", "GENERAL", 1, true),
                reference(ReferenceDataType.OFFERING_CATEGORY, "TITHE", "Tithe", "GENERAL", 2, true)
            ));

        QuarterlyFinancialReport report = service(7).build(member(Role.VIEWER), 2026, 2);

        assertThat(report.sheetName()).isEqualTo("Offering income");
        assertThat(report.titleSuffix()).isEqualTo("수입");
        assertThat(report.specialRowLabel()).isEqualTo("전년도 이월금");
        assertThat(report.specialBudget()).isEqualByComparingTo("2500");
        assertThat(report.specialMonthlyActuals()).isEqualTo(List.of(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("999")
        ));
        assertThat(report.specialCumulativeActual())
            .isEqualTo(new BigDecimal("999"));
        assertThat(report.groups()).extracting(QuarterlyFinancialGroup::groupCode)
            .containsExactly("BUILDING", "GENERAL");
        assertThat(report.groups()).extracting(QuarterlyFinancialGroup::groupLabel)
            .containsExactly("Building Fund", "General Fund");

        QuarterlyFinancialRow project = report.groups().get(0).rows().getFirst();
        assertThat(project.itemLabel()).isEqualTo("Building Project");
        assertThat(project.budget()).isEqualByComparingTo("0");
        assertThat(project.monthlyActuals()).containsExactly(
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50")
        );
        assertThat(project.cumulativeActual()).isEqualByComparingTo("50");

        List<QuarterlyFinancialRow> generalRows = report.groups().get(1).rows();
        assertThat(generalRows).extracting(QuarterlyFinancialRow::itemCode)
            .containsExactly("MISSIONS", "TITHE");
        QuarterlyFinancialRow tithe = generalRows.get(1);
        assertThat(tithe.budget()).isEqualByComparingTo("12000");
        assertThat(tithe.monthlyActuals()).containsExactly(
            new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO
        );
        assertThat(tithe.cumulativeActual()).isEqualByComparingTo("600");
        assertThat(report.groups()).noneMatch(group -> group.groupCode().equals("CARRY_OVER"));
        assertThat(report.groups()).noneMatch(group -> group.groupCode().equalsIgnoreCase("CARRY_OVER"));

        verify(budgetRepository).findActiveByFiscalYear(2025);
        verify(offeringRepository)
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 6, 30)
            );
    }

    @Test
    void fallsBackToStoredCodesAndIgnoresNonOfferingBudgetsAndInvalidAmounts() {
        Budget expense = budget("ADMIN", "OFFICE", "900");
        expense.setBudgetType(BudgetType.EXPENSE);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            expense,
            budget("UNKNOWN_FUND", "UNKNOWN_CATEGORY", null)
        ));
        when(offeringRepository
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31)
            )).thenReturn(List.of(
                offering("2026-01-04", "UNKNOWN_FUND", "UNKNOWN_CATEGORY", null),
                offering("2026-02-01", "UNKNOWN_FUND", "UNKNOWN_CATEGORY", "-5")
            ));

        QuarterlyFinancialReport report = service(1).build(member(Role.ADMIN), 2026, 1);

        assertThat(report.groups()).hasSize(1);
        QuarterlyFinancialGroup fund = report.groups().getFirst();
        assertThat(fund.groupLabel()).isEqualTo("UNKNOWN_FUND");
        assertThat(fund.rows().getFirst().itemLabel()).isEqualTo("UNKNOWN_CATEGORY");
        assertThat(fund.rows().getFirst().budget()).isEqualByComparingTo("0");
        assertThat(fund.rows().getFirst().cumulativeActual()).isEqualByComparingTo("0");
    }

    private QuarterlyOfferingReportService service(int fiscalStartMonth) {
        return new QuarterlyOfferingReportService(
            budgetRepository,
            offeringRepository,
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

    private Budget budget(String fundCode, String categoryCode, String amount) {
        Budget budget = new Budget();
        budget.setFiscalYear(2025);
        budget.setBudgetType(BudgetType.OFFERING_INCOME);
        budget.setCategory(fundCode);
        budget.setSubCategory(categoryCode);
        budget.setBudget(amount == null ? null : new BigDecimal(amount));
        return budget;
    }

    private Offering offering(String sunday, String fundCode, String categoryCode, String amount) {
        Offering offering = new Offering();
        offering.setOfferingSunday(LocalDate.parse(sunday));
        offering.setFundCode(fundCode);
        offering.setCategoryCode(categoryCode);
        offering.setAmount(amount == null ? null : new BigDecimal(amount));
        return offering;
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
