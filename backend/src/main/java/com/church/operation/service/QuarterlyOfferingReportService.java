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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuarterlyOfferingReportService {
    private final BudgetRepository budgetRepository;
    private final OfferingRepository offeringRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public QuarterlyOfferingReportService(
        BudgetRepository budgetRepository,
        OfferingRepository offeringRepository,
        ReferenceDataRepository referenceDataRepository,
        FiscalYearProperties fiscalYearProperties
    ) {
        this.budgetRepository = budgetRepository;
        this.offeringRepository = offeringRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.fiscalYearProperties = fiscalYearProperties;
    }

    public QuarterlyFinancialReport build(Member actor, int year, int quarter) {
        requireReportAccess(actor);
        QuarterlyFinancialPeriod period = QuarterlyFinancialPeriod.from(
            year,
            quarter,
            fiscalYearProperties.startMonth()
        );

        ReportRows reportRows = aggregate(
            period.fiscalBudgetYear(),
            period.fiscalStart(),
            period.quarterStart(),
            period.quarterEnd(),
            period.months()
        );

        return new QuarterlyFinancialReport(
            period.calendarYear(),
            period.quarter(),
            period.quarterStart(),
            period.quarterEnd(),
            period.fiscalBudgetYear(),
            period.fiscalStart(),
            period.months(),
            reportRows.groups(),
            reportRows.carryOverBudget(),
            reportRows.carryOverMonthlyActuals(),
            reportRows.carryOverCumulativeActual(),
            "Offering income",
            "수입",
            "전년도 이월금"
        );
    }

    private ReportRows aggregate(
        int fiscalBudgetYear,
        LocalDate fiscalStart,
        LocalDate quarterStart,
        LocalDate quarterEnd,
        List<YearMonth> months
    ) {
        Map<RowKey, BigDecimal> budgets = new LinkedHashMap<>();
        BigDecimal carryOver = BigDecimal.ZERO;
        for (Budget budget : safeList(budgetRepository.findActiveByFiscalYear(fiscalBudgetYear))) {
            if (budget.getBudgetType() != BudgetType.OFFERING_INCOME) {
                continue;
            }
            String fundCode = normalizeCode(budget.getCategory());
            if (isCarryOverFund(fundCode)) {
                carryOver = carryOver.add(amountOrZero(budget.getBudget()));
                continue;
            }
            String categoryCode = normalizeCode(budget.getSubCategory());
            if (fundCode == null || categoryCode == null) {
                continue;
            }
            budgets.merge(new RowKey(fundCode, categoryCode), amountOrZero(budget.getBudget()), BigDecimal::add);
        }

        Map<RowKey, BigDecimal[]> monthly = new LinkedHashMap<>();
        Map<RowKey, BigDecimal> cumulative = new LinkedHashMap<>();
        BigDecimal[] carryOverMonthly = {
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        };
        BigDecimal carryOverCumulative = BigDecimal.ZERO;
        for (Offering offering : safeList(
            offeringRepository
                .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                    fiscalStart,
                    quarterEnd
                )
        )) {
            String fundCode = normalizeCode(offering.getFundCode());
            String categoryCode = normalizeCode(offering.getCategoryCode());
            LocalDate sunday = offering.getOfferingSunday();
            BigDecimal amount = offering.getAmount();
            if (sunday == null || fundCode == null
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (isCarryOverFund(fundCode)) {
                carryOverCumulative = carryOverCumulative.add(amount);
                if (!sunday.isBefore(quarterStart)) {
                    int monthIndex = months.indexOf(YearMonth.from(sunday));
                    if (monthIndex >= 0) {
                        carryOverMonthly[monthIndex] = carryOverMonthly[monthIndex].add(amount);
                    }
                }
                continue;
            }
            if (categoryCode == null) {
                continue;
            }

            RowKey key = new RowKey(fundCode, categoryCode);
            cumulative.merge(key, amount, BigDecimal::add);
            if (!sunday.isBefore(quarterStart)) {
                int monthIndex = months.indexOf(YearMonth.from(sunday));
                if (monthIndex >= 0) {
                    BigDecimal[] values = monthly.computeIfAbsent(
                        key,
                        ignored -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}
                    );
                    values[monthIndex] = values[monthIndex].add(amount);
                }
            }
        }

        Set<RowKey> rowKeys = new LinkedHashSet<>(budgets.keySet());
        rowKeys.addAll(cumulative.keySet());
        ReferenceIndex references = loadReferenceIndex();
        List<RowKey> sortedKeys = rowKeys.stream()
            .sorted(rowComparator(references))
            .toList();

        Map<String, List<RowKey>> keysByFund = new LinkedHashMap<>();
        for (RowKey key : sortedKeys) {
            keysByFund.computeIfAbsent(key.fundCode(), ignored -> new ArrayList<>()).add(key);
        }

        List<QuarterlyFinancialGroup> groups = new ArrayList<>();
        int sequence = 1;
        for (Map.Entry<String, List<RowKey>> entry : keysByFund.entrySet()) {
            String fundCode = entry.getKey();
            List<QuarterlyFinancialRow> rows = entry.getValue().stream()
                .map(key -> new QuarterlyFinancialRow(
                    key.fundCode(),
                    references.fundLabel(key.fundCode()),
                    key.categoryCode(),
                    references.categoryLabel(key),
                    budgets.getOrDefault(key, BigDecimal.ZERO),
                    List.copyOf(Arrays.asList(monthly.getOrDefault(
                        key,
                        new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}
                    ))),
                    cumulative.getOrDefault(key, BigDecimal.ZERO)
                ))
                .toList();
            groups.add(new QuarterlyFinancialGroup(
                sequence++,
                fundCode,
                references.fundLabel(fundCode),
                rows
            ));
        }
        return new ReportRows(
            List.copyOf(groups),
            carryOver,
            List.copyOf(Arrays.asList(carryOverMonthly)),
            carryOverCumulative
        );
    }

    private ReferenceIndex loadReferenceIndex() {
        Map<String, ReferenceValue> funds = new LinkedHashMap<>();
        for (ReferenceData reference : safeList(
            referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND)
        )) {
            String code = normalizeCode(reference.getCode());
            if (code != null) {
                funds.put(code, new ReferenceValue(labelOrCode(reference.getLabel(), code), reference.getSortOrder()));
            }
        }

        Map<RowKey, ReferenceValue> categories = new LinkedHashMap<>();
        for (ReferenceData reference : safeList(
            referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_CATEGORY)
        )) {
            String code = normalizeCode(reference.getCode());
            String parentCode = normalizeCode(reference.getParentCode());
            if (code != null && parentCode != null) {
                categories.put(
                    new RowKey(parentCode, code),
                    new ReferenceValue(labelOrCode(reference.getLabel(), code), reference.getSortOrder())
                );
            }
        }
        return new ReferenceIndex(funds, categories);
    }

    private Comparator<RowKey> rowComparator(ReferenceIndex references) {
        return Comparator
            .comparingInt((RowKey key) -> references.fund(key.fundCode()).sortOrder())
            .thenComparing(key -> references.fundLabel(key.fundCode()))
            .thenComparing(RowKey::fundCode)
            .thenComparingInt(key -> references.category(key).sortOrder())
            .thenComparing(references::categoryLabel)
            .thenComparing(RowKey::categoryCode);
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean isCarryOverFund(String fundCode) {
        return fundCode != null && "CARRY_OVER".equalsIgnoreCase(fundCode);
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String labelOrCode(String label, String code) {
        return label == null || label.isBlank() ? code : label;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void requireReportAccess(Member actor) {
        if (actor != null && actor.getRoles() != null
            && actor.getRoles().stream().anyMatch(this::isReportRole)) {
            return;
        }
        throw new SecurityException("You do not have permission to view reports.");
    }

    private boolean isReportRole(Role role) {
        return role == Role.ADMIN
            || role == Role.TREASURER
            || role == Role.PASTOR
            || role == Role.VIEWER;
    }

    private record RowKey(String fundCode, String categoryCode) {
    }

    private record ReportRows(
        List<QuarterlyFinancialGroup> groups,
        BigDecimal carryOverBudget,
        List<BigDecimal> carryOverMonthlyActuals,
        BigDecimal carryOverCumulativeActual
    ) {
    }

    private record ReferenceValue(String label, int sortOrder) {
    }

    private record ReferenceIndex(
        Map<String, ReferenceValue> funds,
        Map<RowKey, ReferenceValue> categories
    ) {
        private ReferenceValue fund(String code) {
            return funds.getOrDefault(code, new ReferenceValue(code, Integer.MAX_VALUE));
        }

        private String fundLabel(String code) {
            return fund(code).label();
        }

        private ReferenceValue category(RowKey key) {
            return categories.getOrDefault(
                key,
                new ReferenceValue(key.categoryCode(), Integer.MAX_VALUE)
            );
        }

        private String categoryLabel(RowKey key) {
            return category(key).label();
        }
    }
}
