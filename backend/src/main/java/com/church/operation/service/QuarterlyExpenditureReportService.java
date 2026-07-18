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
public class QuarterlyExpenditureReportService {
    private final BudgetRepository budgetRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public QuarterlyExpenditureReportService(
        BudgetRepository budgetRepository,
        FinancialTransactionRepository transactionRepository,
        ReferenceDataRepository referenceDataRepository,
        FiscalYearProperties fiscalYearProperties
    ) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
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
        ReportRows reportRows = aggregate(period);
        return new QuarterlyFinancialReport(
            period.calendarYear(),
            period.quarter(),
            period.quarterStart(),
            period.quarterEnd(),
            period.fiscalBudgetYear(),
            period.fiscalStart(),
            period.months(),
            reportRows.groups(),
            reportRows.contingencyBudget(),
            reportRows.contingencyMonthlyActuals(),
            reportRows.contingencyCumulativeActual(),
            "Expenditure",
            "지출",
            reportRows.contingencyLabel()
        );
    }

    private ReportRows aggregate(QuarterlyFinancialPeriod period) {
        Map<RowKey, BigDecimal> budgets = new LinkedHashMap<>();
        BigDecimal contingencyBudget = BigDecimal.ZERO;
        for (Budget budget : safeList(
            budgetRepository.findActiveByFiscalYear(period.fiscalBudgetYear())
        )) {
            if (budget.getBudgetType() != BudgetType.EXPENSE) {
                continue;
            }
            String categoryCode = normalizeCode(budget.getCategory());
            if (isContingency(categoryCode)) {
                contingencyBudget = contingencyBudget.add(amountOrZero(budget.getBudget()));
                continue;
            }
            String subCategoryCode = normalizeCode(budget.getSubCategory());
            if (categoryCode == null || subCategoryCode == null) {
                continue;
            }
            budgets.merge(
                new RowKey(categoryCode, subCategoryCode),
                amountOrZero(budget.getBudget()),
                BigDecimal::add
            );
        }

        Map<RowKey, BigDecimal[]> monthly = new LinkedHashMap<>();
        Map<RowKey, BigDecimal> cumulative = new LinkedHashMap<>();
        BigDecimal[] contingencyMonthly = {
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        };
        BigDecimal contingencyCumulative = BigDecimal.ZERO;
        for (FinancialTransaction transaction : safeList(
            transactionRepository.findActiveByTransactionDateBetween(
                period.fiscalStart(),
                period.quarterEnd()
            )
        )) {
            if (transaction.getType() != FinancialTransactionType.EXPENSE) {
                continue;
            }
            String categoryCode = normalizeCode(transaction.getCategory());
            LocalDate transactionDate = transaction.getTransactionDate();
            BigDecimal amount = transaction.getAmount();
            if (transactionDate == null || categoryCode == null
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isContingency(categoryCode)) {
                contingencyCumulative = contingencyCumulative.add(amount);
                addMonthly(
                    contingencyMonthly,
                    period.months(),
                    period.quarterStart(),
                    transactionDate,
                    amount
                );
                continue;
            }

            String subCategoryCode = normalizeCode(transaction.getSubCategory());
            if (subCategoryCode == null) {
                continue;
            }
            RowKey key = new RowKey(categoryCode, subCategoryCode);
            cumulative.merge(key, amount, BigDecimal::add);
            BigDecimal[] monthlyValues = monthly.computeIfAbsent(
                key,
                ignored -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}
            );
            addMonthly(
                monthlyValues,
                period.months(),
                period.quarterStart(),
                transactionDate,
                amount
            );
        }

        Set<RowKey> rowKeys = new LinkedHashSet<>(budgets.keySet());
        rowKeys.addAll(cumulative.keySet());
        ReferenceIndex references = loadReferenceIndex();
        Map<String, List<RowKey>> keysByCategory = new LinkedHashMap<>();
        rowKeys.stream()
            .sorted(rowComparator(references))
            .forEach(key -> keysByCategory
                .computeIfAbsent(key.categoryCode(), ignored -> new ArrayList<>())
                .add(key));

        List<QuarterlyFinancialGroup> groups = new ArrayList<>();
        int sequence = 1;
        for (Map.Entry<String, List<RowKey>> entry : keysByCategory.entrySet()) {
            String categoryCode = entry.getKey();
            List<QuarterlyFinancialRow> rows = entry.getValue().stream()
                .map(key -> new QuarterlyFinancialRow(
                    key.categoryCode(),
                    references.categoryLabel(key.categoryCode()),
                    key.subCategoryCode(),
                    references.subCategoryLabel(key),
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
                categoryCode,
                references.categoryLabel(categoryCode),
                rows
            ));
        }
        return new ReportRows(
            List.copyOf(groups),
            contingencyBudget,
            List.copyOf(Arrays.asList(contingencyMonthly)),
            contingencyCumulative,
            references.categoryLabelIgnoreCase("CONTINGENCY")
        );
    }

    private void addMonthly(
        BigDecimal[] monthly,
        List<YearMonth> months,
        LocalDate quarterStart,
        LocalDate date,
        BigDecimal amount
    ) {
        if (date.isBefore(quarterStart)) {
            return;
        }
        int monthIndex = months.indexOf(YearMonth.from(date));
        if (monthIndex >= 0) {
            monthly[monthIndex] = monthly[monthIndex].add(amount);
        }
    }

    private ReferenceIndex loadReferenceIndex() {
        Map<String, ReferenceValue> categories = new LinkedHashMap<>();
        for (ReferenceData reference : safeList(
            referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(
                ReferenceDataType.FINANCIAL_CATEGORY
            )
        )) {
            String code = normalizeCode(reference.getCode());
            if (code != null) {
                categories.put(
                    code,
                    new ReferenceValue(labelOrCode(reference.getLabel(), code), reference.getSortOrder())
                );
            }
        }

        Map<RowKey, ReferenceValue> subCategories = new LinkedHashMap<>();
        for (ReferenceData reference : safeList(
            referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(
                ReferenceDataType.FINANCIAL_SUB_CATEGORY
            )
        )) {
            String code = normalizeCode(reference.getCode());
            String parentCode = normalizeCode(reference.getParentCode());
            if (code != null && parentCode != null) {
                subCategories.put(
                    new RowKey(parentCode, code),
                    new ReferenceValue(labelOrCode(reference.getLabel(), code), reference.getSortOrder())
                );
            }
        }
        return new ReferenceIndex(categories, subCategories);
    }

    private Comparator<RowKey> rowComparator(ReferenceIndex references) {
        return Comparator
            .comparingInt((RowKey key) -> references.category(key.categoryCode()).sortOrder())
            .thenComparing(key -> references.categoryLabel(key.categoryCode()))
            .thenComparing(RowKey::categoryCode)
            .thenComparingInt(key -> references.subCategory(key).sortOrder())
            .thenComparing(references::subCategoryLabel)
            .thenComparing(RowKey::subCategoryCode);
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean isContingency(String categoryCode) {
        return categoryCode != null && "CONTINGENCY".equalsIgnoreCase(categoryCode);
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

    private record RowKey(String categoryCode, String subCategoryCode) {
    }

    private record ReportRows(
        List<QuarterlyFinancialGroup> groups,
        BigDecimal contingencyBudget,
        List<BigDecimal> contingencyMonthlyActuals,
        BigDecimal contingencyCumulativeActual,
        String contingencyLabel
    ) {
    }

    private record ReferenceValue(String label, int sortOrder) {
    }

    private record ReferenceIndex(
        Map<String, ReferenceValue> categories,
        Map<RowKey, ReferenceValue> subCategories
    ) {
        private ReferenceValue category(String code) {
            return categories.getOrDefault(code, new ReferenceValue(code, Integer.MAX_VALUE));
        }

        private String categoryLabel(String code) {
            return category(code).label();
        }

        private String categoryLabelIgnoreCase(String code) {
            return categories.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(code))
                .map(entry -> entry.getValue().label())
                .findFirst()
                .filter(label -> label != null && !label.isBlank())
                .orElse(code);
        }

        private ReferenceValue subCategory(RowKey key) {
            return subCategories.getOrDefault(
                key,
                new ReferenceValue(key.subCategoryCode(), Integer.MAX_VALUE)
            );
        }

        private String subCategoryLabel(RowKey key) {
            return subCategory(key).label();
        }
    }
}
