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
public class YearlyOfferingReportService {
    private static final String CARRY_OVER = "CARRY_OVER";

    private final BudgetRepository budgetRepository;
    private final OfferingRepository offeringRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final FiscalYearProperties fiscalYearProperties;

    public YearlyOfferingReportService(
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
        List<RowKey> sortedKeys = keys.stream().sorted(rowComparator(references)).toList();

        Map<String, List<RowKey>> keysByFund = new LinkedHashMap<>();
        for (RowKey key : sortedKeys) {
            keysByFund.computeIfAbsent(key.fundCode(), ignored -> new ArrayList<>()).add(key);
        }

        List<YearlyFinancialGroup> groups = new ArrayList<>();
        int sequence = 1;
        for (Map.Entry<String, List<RowKey>> entry : keysByFund.entrySet()) {
            List<YearlyFinancialRow> rows = entry.getValue().stream()
                .map(key -> new YearlyFinancialRow(
                    key.fundCode(),
                    references.fundLabel(key.fundCode()),
                    key.categoryCode(),
                    references.categoryLabel(key),
                    current.values().getOrDefault(key, BigDecimal.ZERO),
                    actual.values().getOrDefault(key, BigDecimal.ZERO),
                    next.values().getOrDefault(key, BigDecimal.ZERO),
                    next.presentKeys().contains(key)
                ))
                .toList();
            groups.add(new YearlyFinancialGroup(
                sequence++,
                entry.getKey(),
                references.fundLabel(entry.getKey()),
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
            "Offering income",
            "수입 결산 및 예산안",
            "수입결산",
            "수입대비",
            "전년도 이월금"
        );
    }

    private BudgetValues loadBudgets(int fiscalYear) {
        Map<RowKey, BigDecimal> values = new LinkedHashMap<>();
        Set<RowKey> presentKeys = new LinkedHashSet<>();
        BigDecimal specialAmount = BigDecimal.ZERO;
        boolean specialPresent = false;
        for (Budget budget : safeList(budgetRepository.findActiveByFiscalYear(fiscalYear))) {
            if (budget.getBudgetType() != BudgetType.OFFERING_INCOME) {
                continue;
            }
            String fundCode = normalizeCode(budget.getCategory());
            if (isCarryOver(fundCode)) {
                specialAmount = specialAmount.add(amountOrZero(budget.getBudget()));
                specialPresent = true;
                continue;
            }
            String categoryCode = normalizeCode(budget.getSubCategory());
            if (fundCode == null || categoryCode == null) {
                continue;
            }
            RowKey key = new RowKey(fundCode, categoryCode);
            values.merge(key, amountOrZero(budget.getBudget()), BigDecimal::add);
            presentKeys.add(key);
        }
        return new BudgetValues(values, presentKeys, specialAmount, specialPresent);
    }

    private ActualValues loadActuals(YearlyFinancialPeriod period) {
        Map<RowKey, BigDecimal> values = new LinkedHashMap<>();
        BigDecimal specialAmount = BigDecimal.ZERO;
        for (Offering offering : safeList(
            offeringRepository
                .findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
                    period.fiscalStart(),
                    period.fiscalEnd()
                )
        )) {
            String fundCode = normalizeCode(offering.getFundCode());
            BigDecimal amount = offering.getAmount();
            if (offering.getOfferingSunday() == null || fundCode == null
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isCarryOver(fundCode)) {
                specialAmount = specialAmount.add(amount);
                continue;
            }
            String categoryCode = normalizeCode(offering.getCategoryCode());
            if (categoryCode != null) {
                values.merge(new RowKey(fundCode, categoryCode), amount, BigDecimal::add);
            }
        }
        return new ActualValues(values, specialAmount);
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

    private boolean isCarryOver(String fundCode) {
        return fundCode != null && CARRY_OVER.equalsIgnoreCase(fundCode);
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

    private record RowKey(String fundCode, String categoryCode) {
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
            return categories.getOrDefault(key, new ReferenceValue(key.categoryCode(), Integer.MAX_VALUE));
        }

        private String categoryLabel(RowKey key) {
            return category(key).label();
        }
    }
}
