package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.DashboardResponse;
import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);

    @Mock private MemberRepository memberRepository;
    @Mock private OfferingRepository offeringRepository;
    @Mock private FinancialTransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;

    @Test
    void returnsFourCardValuesAndTwelveSundayPoints() {
        DashboardService service = service();
        when(memberRepository.findAll()).thenReturn(List.of(
            member(true, Instant.parse("2026-07-02T12:00:00Z")),
            member(true, null, new ObjectId(Date.from(Instant.parse("2026-07-05T12:00:00Z"))).toHexString()),
            member(false, Instant.parse("2026-07-03T12:00:00Z"))
        ));
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of(
            budget(BudgetType.OFFERING_INCOME, "100.00"),
            budget(BudgetType.EXPENSE, "80.00")
        ));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), TODAY
        )).thenReturn(List.of(offering(TODAY, LocalDate.of(2026, 7, 12), "60.00")));
        when(offeringRepository.findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 19)
        )).thenReturn(List.of(
            offering(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 3), "10.00"),
            offering(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), "20.00")
        ));
        FinancialTransaction pending = expense("30.00", "100", false);
        FinancialTransaction cleared = expense("15.00", "101", true);
        when(transactionRepository.findActiveByTransactionDateBetween(LocalDate.of(2026, 1, 1), TODAY))
            .thenReturn(List.of(expense("40.00", null, false)));
        when(transactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc())
            .thenReturn(List.of(pending, cleared));

        DashboardResponse response = service.getDashboard(actor(Role.MEMBERSHIP), TODAY);

        assertThat(response.activeMemberCount()).isEqualTo(2);
        assertThat(response.newMemberCount()).isEqualTo(3);
        assertThat(response.ytdOfferingActual()).isEqualByComparingTo("60.00");
        assertThat(response.ytdOfferingBudget()).isEqualByComparingTo("100.00");
        assertThat(response.ytdOfferingPercentage()).isEqualByComparingTo("60.00");
        assertThat(response.ytdExpenseActual()).isEqualByComparingTo("40.00");
        assertThat(response.ytdExpenseBudget()).isEqualByComparingTo("80.00");
        assertThat(response.ytdExpensePercentage()).isEqualByComparingTo("50.00");
        assertThat(response.pendingChequeCount()).isEqualTo(1);
        assertThat(response.pendingChequeTotal()).isEqualByComparingTo("30.00");
        assertThat(response.weekOfferingTotal()).isEqualByComparingTo("20.00");
        assertThat(response.monthOfferingTotal()).isEqualByComparingTo("20.00");
        assertThat(response.yearOfferingTotal()).isEqualByComparingTo("30.00");
        assertThat(response.offeringTrend()).hasSize(12);
        assertThat(response.offeringTrend().getFirst().sunday()).isEqualTo(LocalDate.of(2026, 5, 3));
        assertThat(response.offeringTrend().getFirst().amount()).isEqualByComparingTo("10.00");
        assertThat(response.offeringTrend().getLast().sunday()).isEqualTo(LocalDate.of(2026, 7, 19));
        assertThat(response.offeringTrend().getLast().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void zeroBudgetsReturnNullPercentages() {
        DashboardService service = service();
        stubEmptyData();

        DashboardResponse response = service.getDashboard(actor(Role.VIEWER), TODAY);

        assertThat(response.ytdOfferingPercentage()).isNull();
        assertThat(response.ytdExpensePercentage()).isNull();
    }

    @Test
    void memberRoleCannotReadStaffDashboard() {
        assertThatThrownBy(() -> service().getDashboard(actor(Role.MEMBER), TODAY))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to view the dashboard.");
    }

    private DashboardService service() {
        return new DashboardService(
            memberRepository,
            offeringRepository,
            transactionRepository,
            budgetRepository,
            new FiscalYearProperties(1)
        );
    }

    private void stubEmptyData() {
        when(memberRepository.findAll()).thenReturn(List.of());
        when(budgetRepository.findActiveByFiscalYear(2026)).thenReturn(List.of());
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), TODAY
        )).thenReturn(List.of());
        when(offeringRepository.findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 19)
        )).thenReturn(List.of());
        when(transactionRepository.findActiveByTransactionDateBetween(LocalDate.of(2026, 1, 1), TODAY))
            .thenReturn(List.of());
        when(transactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc()).thenReturn(List.of());
    }

    private Member actor(Role role) {
        Member actor = new Member();
        actor.setRoles(Set.of(role));
        return actor;
    }

    private Member member(boolean active, Instant createdAt) {
        return member(active, createdAt, null);
    }

    private Member member(boolean active, Instant createdAt, String id) {
        Member member = new Member();
        member.setId(id);
        member.setActive(active);
        member.setCreatedAt(createdAt);
        return member;
    }

    private Budget budget(BudgetType type, String amount) {
        Budget budget = new Budget();
        budget.setBudgetType(type);
        budget.setBudget(new BigDecimal(amount));
        return budget;
    }

    private Offering offering(LocalDate date, LocalDate sunday, String amount) {
        Offering offering = new Offering();
        offering.setOfferingDate(date);
        offering.setOfferingSunday(sunday);
        offering.setAmount(new BigDecimal(amount));
        return offering;
    }

    private FinancialTransaction expense(String amount, String chequeNo, boolean cleared) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setType(FinancialTransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setChequeNo(chequeNo);
        transaction.setChequeCleared(cleared);
        return transaction;
    }
}
