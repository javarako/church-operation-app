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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class YearlyExpenditureReportService {
    private static final String CONTINGENCY = "CONTINGENCY";

    private final BudgetRepository budgetRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public YearlyExpenditureReportService(
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

    public YearlyFinancialReport build(Member actor, int fiscalYear) {
        requireReportAccess(actor);
        YearlyFinancialPeriod period = YearlyFinancialPeriod.from(
            fiscalYear,
            fiscalYearProperties.startMonth()
        );

        BudgetValues current = loadBudgets(fiscalYear);
        BudgetValues next = loadBudgets(fiscalYear + 1);
        ActualValues actual = loadActuals(period);
        ReferenceIndex references = loadReferenceIndex();

        Set<RowKey> keys = new LinkedHashSet<>(current.values().keySet());
        keys.addAll(actual.values().keySet());
        keys.addAll(next.values().keySet());
        Map<String, List<RowKey>> keysByCategory = new LinkedHashMap<>();
        keys.stream()
            .sorted(rowComparator(references))
            .forEach(key -> keysByCategory
                .computeIfAbsent(key.categoryCode(), ignored -> new ArrayList<>())
                .add(key));

        List<YearlyFinancialGroup> groups = new ArrayList<>();
        int sequence = 1;
        for (Map.Entry<String, List<RowKey>> entry : keysByCategory.entrySet()) {
            List<YearlyFinancialRow> rows = entry.getValue().stream()
                .map(key -> new YearlyFinancialRow(
                    key.categoryCode(),
                    references.categoryLabel(key.categoryCode()),
                    key.subCategoryCode(),
                    references.subCategoryLabel(key),
                    current.values().getOrDefault(key, BigDecimal.ZERO),
                    actual.values().getOrDefault(key, BigDecimal.ZERO),
                    next.values().getOrDefault(key, BigDecimal.ZERO),
                    next.presentKeys().contains(key)
                ))
                .toList();
            groups.add(new YearlyFinancialGroup(
                sequence++,
                entry.getKey(),
                references.categoryLabel(entry.getKey()),
                rows
            ));
        }

        return new YearlyFinancialReport(
            fiscalYear,
            period.fiscalStart(),
            period.fiscalEnd(),
            List.copyOf(groups),
            current.specialAmount(),
            actual.specialAmount(),
            next.specialAmount(),
            next.specialPresent(),
            "Expenditure",
            "지출 결산 및 예산안",
            "지출결산",
            "지출대비",
            references.categoryLabelIgnoreCase(CONTINGENCY)
        );
    }

    private BudgetValues loadBudgets(int fiscalYear) {
        Map<RowKey, BigDecimal> values = new LinkedHashMap<>();
        Set<RowKey> presentKeys = new LinkedHashSet<>();
        BigDecimal specialAmount = BigDecimal.ZERO;
        boolean specialPresent = false;
        for (Budget budget : safeList(budgetRepository.findActiveByFiscalYear(fiscalYear))) {
            if (budget.getBudgetType() != BudgetType.EXPENSE) {
                continue;
            }
            String categoryCode = normalizeCode(budget.getCategory());
            if (isContingency(categoryCode)) {
                specialAmount = specialAmount.add(amountOrZero(budget.getBudget()));
                specialPresent = true;
                continue;
            }
            String subCategoryCode = normalizeCode(budget.getSubCategory());
            if (categoryCode == null || subCategoryCode == null) {
                continue;
            }
            RowKey key = new RowKey(categoryCode, subCategoryCode);
            values.merge(key, amountOrZero(budget.getBudget()), BigDecimal::add);
            presentKeys.add(key);
        }
        return new BudgetValues(values, presentKeys, specialAmount, specialPresent);
    }

    private ActualValues loadActuals(YearlyFinancialPeriod period) {
        Map<RowKey, BigDecimal> values = new LinkedHashMap<>();
        BigDecimal specialAmount = BigDecimal.ZERO;
        for (FinancialTransaction transaction : safeList(
            transactionRepository.findActiveByTransactionDateBetween(
                period.fiscalStart(),
                period.fiscalEnd()
            )
        )) {
            if (transaction.getType() != FinancialTransactionType.EXPENSE) {
                continue;
            }
            String categoryCode = normalizeCode(transaction.getCategory());
            BigDecimal amount = transaction.getAmount();
            if (transaction.getTransactionDate() == null || categoryCode == null
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isContingency(categoryCode)) {
                specialAmount = specialAmount.add(amount);
                continue;
            }
            String subCategoryCode = normalizeCode(transaction.getSubCategory());
            if (subCategoryCode != null) {
                values.merge(new RowKey(categoryCode, subCategoryCode), amount, BigDecimal::add);
            }
        }
        return new ActualValues(values, specialAmount);
    }

    private ReferenceIndex loadReferenceIndex() {
        Map<String, ReferenceValue> categories = new LinkedHashMap<>();
        for (ReferenceData reference : safeList(
            referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.FINANCIAL_CATEGORY)
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

    private boolean isContingency(String categoryCode) {
        return categoryCode != null && CONTINGENCY.equalsIgnoreCase(categoryCode);
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String labelOrCode(String label, String code) {
        return label == null || label.isBlank() ? code : label;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record RowKey(String categoryCode, String subCategoryCode) {
    }

    private record BudgetValues(
        Map<RowKey, BigDecimal> values,
        Set<RowKey> presentKeys,
        BigDecimal specialAmount,
        boolean specialPresent
    ) {
    }

    private record ActualValues(Map<RowKey, BigDecimal> values, BigDecimal specialAmount) {
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
