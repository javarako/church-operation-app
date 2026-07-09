package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.FinancialBudgetReportRow;
import com.church.operation.dto.MemberOfferingSummaryReportRow;
import com.church.operation.dto.OfficialTaxReportRow;
import com.church.operation.dto.WeeklyOfferingReportRow;
import com.church.operation.entity.Address;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.GivingType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ReportServiceTest {
    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private BudgetRepository budgetRepository;

    @Test
    void weeklyOfferingReportGroupsActiveOfferings() {
        Member actor = member("viewer-id", Role.VIEWER);
        when(offeringRepository.findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )).thenReturn(List.of(
            offering("1", GivingType.MEMBER, "member-1", "Ada", LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 12), "TITHE", "CASH", "20.00"),
            offering("2", GivingType.MEMBER, "member-2", "Ben", LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 12), "TITHE", "CASH", "30.00"),
            offering("3", GivingType.GROUP, null, "Choir", LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12), "MISSION", "CHEQUE", "40.00"),
            offering("4", GivingType.ANONYMOUS, null, "Anonymous", LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 19), "TITHE", "CASH", "15.00")
        ));

        List<WeeklyOfferingReportRow> rows = service().weeklyOfferings(
            actor,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "TITHE",
            "CASH"
        );

        assertThat(rows).containsExactly(
            new WeeklyOfferingReportRow(LocalDate.of(2026, 7, 12), "TITHE", GivingType.MEMBER, "CASH", 2, new BigDecimal("50.00")),
            new WeeklyOfferingReportRow(LocalDate.of(2026, 7, 19), "TITHE", GivingType.ANONYMOUS, "CASH", 1, new BigDecimal("15.00"))
        );
    }

    @Test
    void memberSummaryExcludesAnonymousAndGroupOfferings() {
        Member actor = member("pastor-id", Role.PASTOR);
        Member member = member("member-1", Role.MEMBER);
        member.setDisplayName("Ada Wong");
        member.setPrimaryEmail("ada@example.com");
        member.setOfferingNumber("OFF-1");
        when(memberRepository.findAll()).thenReturn(List.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            offering("1", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), "TITHE", "CASH", "25.00"),
            offering("2", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 2, 4), LocalDate.of(2026, 2, 8), "TITHE", "CHEQUE", "35.00"),
            offering("3", GivingType.ANONYMOUS, null, "Anonymous", LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 8), "TITHE", "CASH", "40.00"),
            offering("4", GivingType.GROUP, null, "Youth", LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 5), "TITHE", "CASH", "50.00")
        ));

        List<MemberOfferingSummaryReportRow> rows = service().memberOfferings(
            actor,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            "TITHE"
        );

        assertThat(rows).containsExactly(
            new MemberOfferingSummaryReportRow("member-1", "Ada Wong", "ada@example.com", "OFF-1", "TITHE", 2, new BigDecimal("60.00"))
        );
    }

    @Test
    void memberSummaryFiltersAndSortsByOfferingNumber() {
        Member actor = member("pastor-id", Role.PASTOR);
        Member memberTwo = member("member-2", Role.MEMBER);
        memberTwo.setDisplayName("Ben Kim");
        memberTwo.setPrimaryEmail("ben@example.com");
        memberTwo.setOfferingNumber("200");
        Member memberOne = member("member-1", Role.MEMBER);
        memberOne.setDisplayName("Ada Wong");
        memberOne.setPrimaryEmail("ada@example.com");
        memberOne.setOfferingNumber("100");
        when(memberRepository.findAll()).thenReturn(List.of(memberTwo, memberOne));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            offering("1", GivingType.MEMBER, "member-2", "Ben Kim", LocalDate.of(2026, 2, 4), LocalDate.of(2026, 2, 8), "TITHE", "CASH", "20.00"),
            offering("2", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), "TITHE", "CASH", "10.00")
        ));

        List<MemberOfferingSummaryReportRow> rows = service().memberOfferings(
            actor,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            "TITHE"
        );

        assertThat(rows).extracting(MemberOfferingSummaryReportRow::offeringNumber)
            .containsExactly("100", "200");

        List<MemberOfferingSummaryReportRow> filteredRows = service().memberOfferings(
            actor,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            "200",
            "TITHE"
        );

        assertThat(filteredRows).extracting(MemberOfferingSummaryReportRow::memberName)
            .containsExactly("Ben Kim");
    }

    @Test
    void viewerCannotExtractOfficialTaxReport() {
        Member actor = member("viewer-id", Role.VIEWER);

        assertThatThrownBy(() -> service().officialTaxReturn(actor, 2026, null))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to extract official tax reports.");
    }

    @Test
    void taxReportUsesCalendarYearRange() {
        Member actor = member("treasurer-id", Role.TREASURER);
        Member member = member("member-1", Role.MEMBER);
        member.setDisplayName("Ada Wong");
        member.setPrimaryEmail("ada@example.com");
        member.setOfferingNumber("OFF-1");
        member.setMailingAddress(new Address("123 Main St", "Unit 5", "Toronto", "ON", "M1M1M1", "Canada"));
        when(memberRepository.findAll()).thenReturn(List.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            offering("1", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), "TITHE", "CASH", "120.00")
        ));

        List<OfficialTaxReportRow> rows = service().officialTaxReturn(actor, 2026, "OFF-1");

        assertThat(rows).containsExactly(
            new OfficialTaxReportRow(
                "Grace Church",
                "1 Hope Rd",
                "555-1111",
                "Pat Lee",
                2026,
                "member-1",
                "Ada Wong",
                "ada@example.com",
                "OFF-1",
                "123 Main St, Unit 5, Toronto, ON, M1M1M1, Canada",
                LocalDate.of(2026, 6, 15),
                "TITHE",
                new BigDecimal("120.00")
            )
        );
    }

    @Test
    void taxReportFiltersAndSortsByOfferingNumberThenGivingDate() {
        Member actor = member("treasurer-id", Role.TREASURER);
        Member memberTwo = member("member-2", Role.MEMBER);
        memberTwo.setDisplayName("Ben Kim");
        memberTwo.setPrimaryEmail("ben@example.com");
        memberTwo.setOfferingNumber("200");
        Member memberOne = member("member-1", Role.MEMBER);
        memberOne.setDisplayName("Ada Wong");
        memberOne.setPrimaryEmail("ada@example.com");
        memberOne.setOfferingNumber("100");
        when(memberRepository.findAll()).thenReturn(List.of(memberTwo, memberOne));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            offering("1", GivingType.MEMBER, "member-2", "Ben Kim", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), "TITHE", "CASH", "20.00"),
            offering("2", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 15), "TITHE", "CASH", "10.00"),
            offering("3", GivingType.MEMBER, "member-1", "Ada Wong", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11), "MISSION", "CASH", "15.00")
        ));

        List<OfficialTaxReportRow> rows = service().officialTaxReturn(actor, 2026, null);

        assertThat(rows).extracting(row -> row.offeringNumber() + ":" + row.givingDate())
            .containsExactly("100:2026-01-10", "100:2026-02-10", "200:2026-03-10");

        List<OfficialTaxReportRow> filteredRows = service().officialTaxReturn(actor, 2026, "200");

        assertThat(filteredRows).extracting(OfficialTaxReportRow::memberName)
            .containsExactly("Ben Kim");
    }

    @Test
    void financialReportUsesConfiguredFiscalYearRange() {
        Member actor = member("viewer-id", Role.VIEWER);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.OFFERING_INCOME, "TITHE", null, "1000.00"),
            budget(BudgetType.EXPENSE, "OFFICE", "SUPPLIES", "300.00")
        ));
        when(financialTransactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.INCOME, FinancialSourceType.OFFERING, "TITHE", null, "750.00"),
            transaction(FinancialTransactionType.EXPENSE, FinancialSourceType.MANUAL, "OFFICE", "SUPPLIES", "125.00")
        ));

        List<FinancialBudgetReportRow> rows = service().financialBudget(actor, 2026);

        assertThat(rows).containsExactly(
            new FinancialBudgetReportRow(2026, BudgetType.OFFERING_INCOME, "TITHE", null, new BigDecimal("1000.00"), new BigDecimal("750.00"), new BigDecimal("-250.00")),
            new FinancialBudgetReportRow(2026, BudgetType.EXPENSE, "OFFICE", "SUPPLIES", new BigDecimal("300.00"), new BigDecimal("125.00"), new BigDecimal("-175.00"))
        );
    }

    @Test
    void financialReportIncludesCarryOverBudget() {
        Member actor = member("admin-id", Role.ADMIN);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.CARRY_OVER, null, null, "500.00")
        ));
        when(financialTransactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of());

        List<FinancialBudgetReportRow> rows = service().financialBudget(actor, 2026);

        assertThat(rows).containsExactly(
            new FinancialBudgetReportRow(2026, BudgetType.CARRY_OVER, null, null, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("-500.00"))
        );
    }

    @Test
    void financialReportIncludesActualsWithoutMatchingBudgetRows() {
        Member actor = member("viewer-id", Role.VIEWER);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.OFFERING_INCOME, "TITHE", null, "1000.00")
        ));
        when(financialTransactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.INCOME, FinancialSourceType.OFFERING, "TITHE", null, "750.00"),
            transaction(FinancialTransactionType.EXPENSE, FinancialSourceType.MANUAL, "OUTREACH", "EVENTS", "90.00")
        ));

        List<FinancialBudgetReportRow> rows = service().financialBudget(actor, 2026);

        assertThat(rows).containsExactly(
            new FinancialBudgetReportRow(2026, BudgetType.OFFERING_INCOME, "TITHE", null, new BigDecimal("1000.00"), new BigDecimal("750.00"), new BigDecimal("-250.00")),
            new FinancialBudgetReportRow(2026, BudgetType.EXPENSE, "OUTREACH", "EVENTS", BigDecimal.ZERO, new BigDecimal("90.00"), new BigDecimal("90.00"))
        );
    }

    @Test
    void taxReportUsesCalendarYearRangeArguments() {
        Member actor = member("treasurer-id", Role.TREASURER);
        when(memberRepository.findAll()).thenReturn(List.of());
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of());

        service().officialTaxReturn(actor, 2026, null);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(offeringRepository).findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            startCaptor.capture(),
            endCaptor.capture()
        );
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void taxReportRejectsInvalidTaxYear() {
        Member actor = member("treasurer-id", Role.TREASURER);

        assertThatThrownBy(() -> service().officialTaxReturn(actor, 0, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tax year is required.");
    }

    @Test
    void financialReportUsesActiveTransactionsWithinFiscalYearRange() {
        Member actor = member("viewer-id", Role.VIEWER);
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.OFFERING_INCOME, "TITHE", null, "1000.00"),
            budget(BudgetType.EXPENSE, "UTILITIES", null, "400.00")
        ));
        when(financialTransactionRepository.findActiveByTransactionDateBetween(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2027, 3, 31)
        )).thenReturn(List.of(
            transaction(FinancialTransactionType.INCOME, FinancialSourceType.OFFERING, "TITHE", null, "750.00")
        ));

        List<FinancialBudgetReportRow> rows = service().financialBudget(actor, 2026);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(financialTransactionRepository).findActiveByTransactionDateBetween(
            startCaptor.capture(),
            endCaptor.capture()
        );

        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(rows).containsExactly(
            new FinancialBudgetReportRow(2026, BudgetType.OFFERING_INCOME, "TITHE", null, new BigDecimal("1000.00"), new BigDecimal("750.00"), new BigDecimal("-250.00")),
            new FinancialBudgetReportRow(2026, BudgetType.EXPENSE, "UTILITIES", null, new BigDecimal("400.00"), BigDecimal.ZERO, new BigDecimal("-400.00"))
        );
    }

    @Test
    void financialReportRejectsInvalidFiscalYear() {
        Member actor = member("viewer-id", Role.VIEWER);

        assertThatThrownBy(() -> service().financialBudget(actor, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fiscal year is required.");
    }

    private ReportService service() {
        return new ReportService(
            offeringRepository,
            memberRepository,
            financialTransactionRepository,
            budgetRepository,
            new ChurchInformationProperties(
                new ChurchInformationProperties.Information("Grace Church", "1 Hope Rd", "555-1111", "Pat Lee"),
                new ChurchInformationProperties.Branding(null, null)
            ),
            new FiscalYearProperties(4)
        );
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setRoles(Set.of(role));
        return member;
    }

    private Offering offering(
        String id,
        GivingType givingType,
        String memberId,
        String displayName,
        LocalDate offeringDate,
        LocalDate offeringSunday,
        String fundCategory,
        String paymentMethod,
        String amount
    ) {
        Offering offering = new Offering();
        offering.setId(id);
        offering.setGivingType(givingType);
        offering.setMemberId(memberId);
        offering.setGiverDisplayName(displayName);
        offering.setOfferingDate(offeringDate);
        offering.setOfferingSunday(offeringSunday);
        offering.setFundCategory(fundCategory);
        offering.setPaymentMethod(paymentMethod);
        offering.setAmount(new BigDecimal(amount));
        return offering;
    }

    private Budget budget(BudgetType budgetType, String category, String subCategory, String amount) {
        Budget budget = new Budget();
        budget.setFiscalYear(2026);
        budget.setBudgetType(budgetType);
        budget.setCategory(category);
        budget.setSubCategory(subCategory);
        budget.setBudget(new BigDecimal(amount));
        return budget;
    }

    private FinancialTransaction transaction(
        FinancialTransactionType type,
        FinancialSourceType sourceType,
        String category,
        String subCategory,
        String amount
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setType(type);
        transaction.setSourceType(sourceType);
        transaction.setCategory(category);
        transaction.setSubCategory(subCategory);
        transaction.setAmount(new BigDecimal(amount));
        return transaction;
    }
}
