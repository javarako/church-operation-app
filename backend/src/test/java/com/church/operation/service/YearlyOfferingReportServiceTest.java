package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.YearlyFinancialGroup;
import com.church.operation.dto.YearlyFinancialReport;
import com.church.operation.dto.YearlyFinancialRow;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YearlyOfferingReportServiceTest {
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void aggregatesSelectedFiscalYearAndSeparatesCarryOver() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, "GENERAL", "TITHE", "12000"),
            budget(2026, "CARRY_OVER", null, "2500")
        ));
        when(budgetRepository.findActiveByFiscalYear(2027)).thenReturn(List.of(
            budget(2027, "GENERAL", "TITHE", "13000"),
            budget(2027, "MISSIONS", "OUTREACH", "3000"),
            budget(2027, "carry_over", null, "2750")
        ));
        when(offeringRepository
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30)
            )).thenReturn(List.of(
                offering("2026-07-05", "GENERAL", "TITHE", "400"),
                offering("2027-06-27", "GENERAL", "TITHE", "500"),
                offering("2026-07-12", "CARRY_OVER", null, "500")
            ));
        mockReferences();

        YearlyFinancialReport report = service(7).build(member(Role.TREASURER), 2026);

        assertThat(report.fiscalStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(report.fiscalEnd()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(report.sheetName()).isEqualTo("Offering income");
        assertThat(report.titleSuffix()).isEqualTo("수입 결산 및 예산안");
        assertThat(report.actualHeader()).isEqualTo("수입결산");
        assertThat(report.actualRatioHeader()).isEqualTo("수입대비");
        assertThat(report.specialRowLabel()).isEqualTo("전년도 이월금");

        YearlyFinancialRow tithe = findRow(report, "TITHE");
        assertThat(tithe.currentBudget()).isEqualByComparingTo("12000");
        assertThat(tithe.actual()).isEqualByComparingTo("900");
        assertThat(tithe.nextBudget()).isEqualByComparingTo("13000");
        assertThat(tithe.nextBudgetPresent()).isTrue();

        YearlyFinancialRow outreach = findRow(report, "OUTREACH");
        assertThat(outreach.currentBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outreach.actual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outreach.nextBudget()).isEqualByComparingTo("3000");
        assertThat(outreach.nextBudgetPresent()).isTrue();

        assertThat(report.specialCurrentBudget()).isEqualByComparingTo("2500");
        assertThat(report.specialActual()).isEqualByComparingTo("500");
        assertThat(report.specialNextBudget()).isEqualByComparingTo("2750");
        assertThat(report.specialNextBudgetPresent()).isTrue();
        assertThat(report.groups()).noneMatch(group -> group.groupCode().equalsIgnoreCase("CARRY_OVER"));

        verify(budgetRepository).findActiveByFiscalYear(2026);
        verify(budgetRepository).findActiveByFiscalYear(2027);
    }

    @Test
    void distinguishesMissingNextBudgetFromEnteredZero() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, "GENERAL", "TITHE", "12000"),
            budget(2026, "GENERAL", "MISSIONS", "2400")
        ));
        when(budgetRepository.findActiveByFiscalYear(2027)).thenReturn(List.of(
            budget(2027, "GENERAL", "MISSIONS", "0")
        ));

        YearlyFinancialReport report = service(1).build(member(Role.VIEWER), 2026);

        assertThat(findRow(report, "TITHE").nextBudgetPresent()).isFalse();
        assertThat(findRow(report, "MISSIONS").nextBudgetPresent()).isTrue();
        assertThat(findRow(report, "MISSIONS").nextBudget()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ordersByReferenceDataAndUsesInactiveLabelsAndCodeFallbacks() {
        Budget expense = budget(2026, "ADMIN", "OFFICE", "900");
        expense.setBudgetType(BudgetType.EXPENSE);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            expense,
            budget(2026, "GENERAL", "TITHE", null),
            budget(2026, "BUILDING", "PROJECT", "100")
        ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_FUND, "BUILDING", "Building Fund", null, 1, false),
                reference(ReferenceDataType.OFFERING_FUND, "GENERAL", "General Fund", null, 2, true)
            ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_CATEGORY))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_CATEGORY, "PROJECT", "", "BUILDING", 1, false),
                reference(ReferenceDataType.OFFERING_CATEGORY, "TITHE", "Tithe", "GENERAL", 1, true)
            ));

        YearlyFinancialReport report = service(1).build(member(Role.ADMIN), 2026);

        assertThat(report.groups()).extracting(YearlyFinancialGroup::groupCode)
            .containsExactly("BUILDING", "GENERAL");
        assertThat(report.groups().getFirst().groupLabel()).isEqualTo("Building Fund");
        assertThat(report.groups().getFirst().rows().getFirst().itemLabel()).isEqualTo("PROJECT");
        assertThat(findRow(report, "TITHE").currentBudget()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ignoresMalformedKeysAndNonPositiveActuals() {
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(2026, "GENERAL", null, "100"),
            budget(2026, " ", "TITHE", "100")
        ));
        when(offeringRepository
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
            )).thenReturn(List.of(
                offering("2026-01-04", "GENERAL", "TITHE", null),
                offering("2026-01-11", "GENERAL", "TITHE", "0"),
                offering("2026-01-18", "GENERAL", "TITHE", "-5"),
                offering("2026-01-25", "GENERAL", "TITHE", "25")
            ));

        YearlyFinancialReport report = service(1).build(member(Role.PASTOR), 2026);

        assertThat(report.groups()).hasSize(1);
        assertThat(findRow(report, "TITHE").actual()).isEqualByComparingTo("25");
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
        assertThatThrownBy(() -> service(13).build(member(Role.VIEWER), 2026))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A valid fiscal year is required.");
    }

    private YearlyOfferingReportService service(int fiscalStartMonth) {
        return new YearlyOfferingReportService(
            budgetRepository,
            offeringRepository,
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

    private Budget budget(int fiscalYear, String fundCode, String categoryCode, String amount) {
        Budget budget = new Budget();
        budget.setFiscalYear(fiscalYear);
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

    private void mockReferences() {
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_FUND, "GENERAL", "General Fund", null, 1, true),
                reference(ReferenceDataType.OFFERING_FUND, "MISSIONS", "Missions Fund", null, 2, true)
            ));
        when(referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_CATEGORY))
            .thenReturn(List.of(
                reference(ReferenceDataType.OFFERING_CATEGORY, "TITHE", "Tithe", "GENERAL", 1, true),
                reference(ReferenceDataType.OFFERING_CATEGORY, "OUTREACH", "Outreach", "MISSIONS", 1, true)
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
