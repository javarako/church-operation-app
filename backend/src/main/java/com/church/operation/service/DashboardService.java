package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.DashboardResponse;
import com.church.operation.dto.DashboardTrendPoint;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DashboardService {
    private static final Set<Role> STAFF_ROLES = EnumSet.of(
        Role.ADMIN, Role.TREASURER, Role.PASTOR, Role.VIEWER, Role.MEMBERSHIP
    );

    private final MemberRepository memberRepository;
    private final OfferingRepository offeringRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public DashboardService(
        MemberRepository memberRepository,
        OfferingRepository offeringRepository,
        FinancialTransactionRepository transactionRepository,
        BudgetRepository budgetRepository,
        FiscalYearProperties fiscalYearProperties
    ) {
        this.memberRepository = memberRepository;
        this.offeringRepository = offeringRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.fiscalYearProperties = fiscalYearProperties;
    }

    public DashboardResponse getDashboard(Member actor, LocalDate today) {
        requireStaffRole(actor);

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate calendarYearStart = today.withDayOfYear(1);
        int fiscalStartYear = today.getMonthValue() >= fiscalYearProperties.startMonth()
            ? today.getYear()
            : today.getYear() - 1;
        LocalDate fiscalStart = LocalDate.of(fiscalStartYear, fiscalYearProperties.startMonth(), 1);
        LocalDate fiscalEnd = fiscalStart.plusYears(1).minusDays(1);
        LocalDate latestSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        List<LocalDate> sundays = IntStream.range(0, 12)
            .mapToObj(index -> latestSunday.minusWeeks(11L - index))
            .toList();
        LocalDate offeringQueryStart = calendarYearStart.isBefore(sundays.getFirst())
            ? calendarYearStart
            : sundays.getFirst();

        List<Member> members = memberRepository.findAll();
        long activeMembers = members.stream().filter(Member::isActive).count();
        long newMembers = members.stream()
            .filter(member -> member.getCreatedAt() != null)
            .map(member -> member.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
            .filter(created -> !created.isBefore(monthStart) && !created.isAfter(today))
            .count();

        List<Budget> budgets = budgetRepository.findActiveByFiscalYear(fiscalStartYear);
        BigDecimal offeringBudget = sumBudgets(budgets, BudgetType.OFFERING_INCOME);
        BigDecimal expenseBudget = sumBudgets(budgets, BudgetType.EXPENSE);

        BigDecimal offeringActual = sumOfferings(
            offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(fiscalStart, today),
            ignored -> true
        );

        List<FinancialTransaction> fiscalTransactions =
            transactionRepository.findActiveByTransactionDateBetween(fiscalStart, today);
        BigDecimal expenseActual = sumTransactions(
            fiscalTransactions,
            transaction -> transaction.getType() == FinancialTransactionType.EXPENSE
        );

        List<FinancialTransaction> pendingCheques = transactionRepository
            .findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc().stream()
            .filter(transaction -> transaction.getType() == FinancialTransactionType.EXPENSE)
            .filter(transaction -> transaction.getChequeNo() != null && !transaction.getChequeNo().isBlank())
            .filter(transaction -> !transaction.isChequeCleared())
            .toList();

        List<Offering> overviewOfferings = offeringRepository
            .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                offeringQueryStart, today
            );
        BigDecimal weekOffering = sumOfferings(overviewOfferings,
            offering -> !offering.getOfferingSunday().isBefore(latestSunday));
        BigDecimal monthOffering = sumOfferings(overviewOfferings,
            offering -> !offering.getOfferingSunday().isBefore(monthStart));
        BigDecimal yearOffering = sumOfferings(overviewOfferings,
            offering -> !offering.getOfferingSunday().isBefore(calendarYearStart));

        Map<LocalDate, BigDecimal> totalsBySunday = overviewOfferings.stream()
            .filter(offering -> offering.getOfferingSunday() != null)
            .collect(Collectors.toMap(
                Offering::getOfferingSunday,
                offering -> amountOrZero(offering.getAmount()),
                BigDecimal::add
            ));
        List<DashboardTrendPoint> trend = sundays.stream()
            .map(sunday -> new DashboardTrendPoint(sunday, totalsBySunday.getOrDefault(sunday, BigDecimal.ZERO)))
            .toList();

        return new DashboardResponse(
            activeMembers,
            newMembers,
            offeringActual,
            offeringBudget,
            percentage(offeringActual, offeringBudget),
            expenseActual,
            expenseBudget,
            percentage(expenseActual, expenseBudget),
            pendingCheques.size(),
            sumTransactions(pendingCheques, ignored -> true),
            weekOffering,
            monthOffering,
            yearOffering,
            fiscalStart,
            fiscalEnd,
            trend
        );
    }

    private BigDecimal sumBudgets(List<Budget> budgets, BudgetType type) {
        return budgets.stream()
            .filter(budget -> budget.getBudgetType() == type)
            .map(Budget::getBudget)
            .map(this::amountOrZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumOfferings(List<Offering> offerings, Predicate<Offering> filter) {
        return offerings.stream()
            .filter(filter)
            .map(Offering::getAmount)
            .map(this::amountOrZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTransactions(
        List<FinancialTransaction> transactions,
        Predicate<FinancialTransaction> filter
    ) {
        return transactions.stream()
            .filter(filter)
            .map(FinancialTransaction::getAmount)
            .map(this::amountOrZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentage(BigDecimal actual, BigDecimal budget) {
        if (budget == null || budget.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return actual.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void requireStaffRole(Member actor) {
        if (actor == null || actor.getRoles() == null || actor.getRoles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw new SecurityException("You do not have permission to view the dashboard.");
        }
    }
}
