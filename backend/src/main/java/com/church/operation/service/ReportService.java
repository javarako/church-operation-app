package com.church.operation.service;

import com.church.operation.config.FiscalYearProperties;
import com.church.operation.dto.FinancialBudgetReportRow;
import com.church.operation.dto.MemberOfferingSummaryReportRow;
import com.church.operation.dto.WeeklyOfferingReportRow;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ReportService {
    private final OfferingRepository offeringRepository;
    private final MemberRepository memberRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BudgetRepository budgetRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public ReportService(
        OfferingRepository offeringRepository,
        MemberRepository memberRepository,
        FinancialTransactionRepository financialTransactionRepository,
        BudgetRepository budgetRepository,
        FiscalYearProperties fiscalYearProperties
    ) {
        this.offeringRepository = offeringRepository;
        this.memberRepository = memberRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.budgetRepository = budgetRepository;
        this.fiscalYearProperties = fiscalYearProperties;
    }

    public List<WeeklyOfferingReportRow> weeklyOfferings(
        Member actor,
        LocalDate start,
        LocalDate end,
        String fundCategory,
        String paymentMethod
    ) {
        requireReportAccess(actor);
        validateRange(start, end);

        Map<WeeklyOfferingKey, Summary> grouped = new LinkedHashMap<>();
        for (Offering offering : offeringRepository.findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(start, end)) {
            if (!matches(offering.getFundCategory(), fundCategory) || !matches(offering.getPaymentMethod(), paymentMethod)) {
                continue;
            }

            WeeklyOfferingKey key = new WeeklyOfferingKey(
                offering.getOfferingSunday(),
                offering.getFundCategory(),
                offering.getGivingType(),
                offering.getPaymentMethod()
            );
            grouped.computeIfAbsent(key, ignored -> new Summary()).add(offering.getAmount());
        }

        return grouped.entrySet().stream()
            .sorted(Comparator
                .comparing((Map.Entry<WeeklyOfferingKey, Summary> entry) -> entry.getKey().offeringSunday())
                .thenComparing(entry -> entry.getKey().fundCategory(), Comparator.nullsFirst(String::compareTo))
                .thenComparing(entry -> entry.getKey().givingType(), Comparator.nullsFirst(Enum::compareTo))
                .thenComparing(entry -> entry.getKey().paymentMethod(), Comparator.nullsFirst(String::compareTo)))
            .map(entry -> new WeeklyOfferingReportRow(
                entry.getKey().offeringSunday(),
                entry.getKey().fundCategory(),
                entry.getKey().givingType(),
                entry.getKey().paymentMethod(),
                entry.getValue().count(),
                entry.getValue().total()
            ))
            .toList();
    }

    public List<MemberOfferingSummaryReportRow> memberOfferings(
        Member actor,
        LocalDate start,
        LocalDate end,
        String offeringNumber,
        String fundCategory
    ) {
        requireReportAccess(actor);
        validateRange(start, end);

        Map<String, Member> membersById = new LinkedHashMap<>();
        for (Member member : memberRepository.findAll()) {
            membersById.put(member.getId(), member);
        }

        Map<MemberOfferingKey, Summary> grouped = new LinkedHashMap<>();
        for (Offering offering : offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(start, end)) {
            if (offering.getGivingType() != GivingType.MEMBER) {
                continue;
            }
            Member member = membersById.get(offering.getMemberId());
            if (!matches(member != null ? member.getOfferingNumber() : null, offeringNumber) || !matches(offering.getFundCategory(), fundCategory)) {
                continue;
            }
            String memberName = member != null && member.getDisplayName() != null ? member.getDisplayName() : offering.getGiverDisplayName();
            String primaryEmail = member != null ? member.getPrimaryEmail() : null;
            String memberOfferingNumber = member != null ? member.getOfferingNumber() : null;
            MemberOfferingKey key = new MemberOfferingKey(
                offering.getMemberId(),
                memberName,
                primaryEmail,
                memberOfferingNumber,
                offering.getFundCategory()
            );
            grouped.computeIfAbsent(key, ignored -> new Summary()).add(offering.getAmount());
        }

        return grouped.entrySet().stream()
            .map(entry -> new MemberOfferingSummaryReportRow(
                entry.getKey().memberId(),
                entry.getKey().memberName(),
                entry.getKey().primaryEmail(),
                entry.getKey().offeringNumber(),
                entry.getKey().fundCategory(),
                entry.getValue().count(),
                entry.getValue().total()
            ))
            .sorted(Comparator
                .comparing(MemberOfferingSummaryReportRow::offeringNumber, Comparator.nullsLast(String::compareTo))
                .thenComparing(MemberOfferingSummaryReportRow::memberName, Comparator.nullsLast(String::compareTo))
                .thenComparing(MemberOfferingSummaryReportRow::fundCategory, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public List<FinancialBudgetReportRow> financialBudget(Member actor, int fiscalYear) {
        requireReportAccess(actor);
        validateYear(fiscalYear, "Fiscal year is required.");

        LocalDate start = LocalDate.of(fiscalYear, fiscalYearProperties.startMonth(), 1);
        LocalDate end = start.plusYears(1).minusDays(1);
        List<Budget> budgets = budgetRepository.findActiveByFiscalYear(fiscalYear);
        List<FinancialTransaction> transactions = financialTransactionRepository.findActiveByTransactionDateBetween(start, end);

        Map<BudgetActualKey, BigDecimal> actuals = new LinkedHashMap<>();
        for (FinancialTransaction transaction : transactions) {
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BudgetType budgetType = mapBudgetType(transaction);
            if (budgetType == null) {
                continue;
            }

            BudgetActualKey key = new BudgetActualKey(budgetType, transaction.getCategory(), transaction.getSubCategory());
            actuals.merge(key, transaction.getAmount(), BigDecimal::add);
        }

        Map<BudgetActualKey, Budget> budgetsByKey = new LinkedHashMap<>();
        List<Budget> carryOverBudgets = budgets.stream()
            .filter(budget -> budget.getBudgetType() == BudgetType.CARRY_OVER)
            .toList();

        for (Budget budget : budgets) {
            if (budget.getBudgetType() == BudgetType.CARRY_OVER) {
                continue;
            }
            budgetsByKey.put(new BudgetActualKey(budget.getBudgetType(), budget.getCategory(), budget.getSubCategory()), budget);
        }

        LinkedHashSet<BudgetActualKey> rowKeys = new LinkedHashSet<>(budgetsByKey.keySet());
        rowKeys.addAll(actuals.keySet());

        return Stream.concat(
                carryOverBudgets.stream()
                    .map(budget -> {
                        BigDecimal planned = budget.getBudget() != null ? budget.getBudget() : BigDecimal.ZERO;
                        return new FinancialBudgetReportRow(
                            fiscalYear,
                            budget.getBudgetType(),
                            budget.getCategory(),
                            budget.getSubCategory(),
                            planned,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO.subtract(planned)
                        );
                    }),
                rowKeys.stream().map(key -> {
                    Budget budget = budgetsByKey.get(key);
                    BigDecimal planned = budget != null && budget.getBudget() != null ? budget.getBudget() : BigDecimal.ZERO;
                    BigDecimal actual = actuals.getOrDefault(key, BigDecimal.ZERO);
                    return new FinancialBudgetReportRow(
                        fiscalYear,
                        key.budgetType(),
                        key.category(),
                        key.subCategory(),
                        planned,
                        actual,
                        actual.subtract(planned)
                    );
                })
            )
            .sorted(Comparator
                .comparing(FinancialBudgetReportRow::budgetType)
                .thenComparing(FinancialBudgetReportRow::category, Comparator.nullsFirst(String::compareTo))
                .thenComparing(FinancialBudgetReportRow::subCategory, Comparator.nullsFirst(String::compareTo)))
            .toList();
    }

    private BudgetType mapBudgetType(FinancialTransaction transaction) {
        if (transaction.getType() == FinancialTransactionType.INCOME) {
            return transaction.getSourceType() == FinancialSourceType.OFFERING ? BudgetType.OFFERING_INCOME : null;
        }
        if (transaction.getType() == FinancialTransactionType.EXPENSE) {
            return BudgetType.EXPENSE;
        }
        return null;
    }

    private void requireReportAccess(Member actor) {
        if (hasAnyRole(actor, Role.ADMIN, Role.TREASURER, Role.PASTOR, Role.VIEWER)) {
            return;
        }
        throw new SecurityException("You do not have permission to view reports.");
    }

    private boolean hasAnyRole(Member actor, Role... roles) {
        if (actor == null || actor.getRoles() == null) {
            return false;
        }
        for (Role role : roles) {
            if (actor.getRoles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("A valid date range is required.");
        }
    }

    private void validateYear(int year, String message) {
        if (year <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean matches(String value, String filter) {
        return filter == null || filter.isBlank() || Objects.equals(value, filter);
    }

    private record WeeklyOfferingKey(
        LocalDate offeringSunday,
        String fundCategory,
        GivingType givingType,
        String paymentMethod
    ) {
    }

    private record MemberOfferingKey(
        String memberId,
        String memberName,
        String primaryEmail,
        String offeringNumber,
        String fundCategory
    ) {
    }

    private record BudgetActualKey(BudgetType budgetType, String category, String subCategory) {
    }

    private static final class Summary {
        private long count;
        private BigDecimal total = BigDecimal.ZERO;

        void add(BigDecimal amount) {
            count++;
            if (amount != null) {
                total = total.add(amount);
            }
        }

        long count() {
            return count;
        }

        BigDecimal total() {
            return total;
        }
    }
}
