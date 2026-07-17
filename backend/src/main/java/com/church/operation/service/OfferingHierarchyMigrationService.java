package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.ReferenceDataType;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Service
public class OfferingHierarchyMigrationService {
    static final String GENERAL_FUND = "GENERAL";
    private static final Logger log = LoggerFactory.getLogger(OfferingHierarchyMigrationService.class);

    private final ReferenceDataRepository references;
    private final OfferingRepository offerings;
    private final FinancialTransactionRepository transactions;
    private final BudgetRepository budgets;

    public OfferingHierarchyMigrationService(
        ReferenceDataRepository references,
        OfferingRepository offerings,
        FinancialTransactionRepository transactions,
        BudgetRepository budgets
    ) {
        this.references = references;
        this.offerings = offerings;
        this.transactions = transactions;
        this.budgets = budgets;
    }

    public void migrate() {
        ensureGeneralFund();
        migrateLegacyReferences();
        Map<String, String> legacyOfferingCategories = migrateOfferings();
        migrateLinkedIncome(legacyOfferingCategories);
        migrateOfferingBudgets();
    }

    private void ensureGeneralFund() {
        if (references.findByTypeAndCode(ReferenceDataType.OFFERING_FUND, GENERAL_FUND).isPresent()) {
            return;
        }
        ReferenceData general = new ReferenceData();
        general.setType(ReferenceDataType.OFFERING_FUND);
        general.setCode(GENERAL_FUND);
        general.setLabel("General Fund");
        general.setSortOrder(10);
        general.setActive(true);
        references.save(general);
    }

    private void migrateLegacyReferences() {
        for (ReferenceData legacy : references.findByTypeOrderBySortOrderAscLabelAsc(
            ReferenceDataType.OFFERING_FUND_CATEGORY
        )) {
            if (references.findByTypeAndCode(ReferenceDataType.OFFERING_CATEGORY, legacy.getCode()).isPresent()) {
                continue;
            }
            ReferenceData category = new ReferenceData();
            category.setType(ReferenceDataType.OFFERING_CATEGORY);
            category.setCode(legacy.getCode());
            category.setLabel(legacy.getLabel());
            category.setParentCode(GENERAL_FUND);
            category.setSortOrder(legacy.getSortOrder());
            category.setActive(legacy.isActive());
            references.save(category);
        }
    }

    private Map<String, String> migrateOfferings() {
        Map<String, String> legacyCategories = new HashMap<>();
        for (Offering offering : offerings.findAll()) {
            String legacyCategory = trimToNull(offering.getFundCategory());
            if (legacyCategory == null) {
                continue;
            }
            String fundCode = trimToNull(offering.getFundCode());
            String categoryCode = trimToNull(offering.getCategoryCode());
            boolean compatibleFund = fundCode == null || GENERAL_FUND.equals(fundCode);
            boolean compatibleCategory = categoryCode == null || legacyCategory.equals(categoryCode);
            if (!compatibleFund || !compatibleCategory) {
                log.warn("Skipping conflicting offering hierarchy migration for offering {}", offering.getId());
                continue;
            }
            legacyCategories.put(offering.getId(), legacyCategory);
            if (fundCode == null || categoryCode == null) {
                offering.setFundCode(GENERAL_FUND);
                offering.setCategoryCode(legacyCategory);
                offerings.save(offering);
            }
        }
        return legacyCategories;
    }

    private void migrateLinkedIncome(Map<String, String> legacyOfferingCategories) {
        for (FinancialTransaction transaction : transactions.findAll()) {
            if (transaction.getSourceType() != FinancialSourceType.OFFERING
                || trimToNull(transaction.getSubCategory()) != null) {
                continue;
            }
            String legacyCategory = legacyOfferingCategories.get(transaction.getSourceId());
            if (legacyCategory == null) {
                continue;
            }
            String currentFund = trimToNull(transaction.getCategory());
            if (currentFund != null
                && !GENERAL_FUND.equals(currentFund)
                && !legacyCategory.equals(currentFund)) {
                log.warn("Skipping conflicting linked income hierarchy migration for transaction {}",
                    transaction.getId());
                continue;
            }
            transaction.setCategory(GENERAL_FUND);
            transaction.setSubCategory(legacyCategory);
            transactions.save(transaction);
        }
    }

    private void migrateOfferingBudgets() {
        for (Budget budget : budgets.findAll()) {
            if (budget.getBudgetType() != BudgetType.OFFERING_INCOME
                || trimToNull(budget.getSubCategory()) != null
                || trimToNull(budget.getCategory()) == null
                || GENERAL_FUND.equals(budget.getCategory())) {
                continue;
            }
            String legacyCategory = budget.getCategory();
            budget.setCategory(GENERAL_FUND);
            budget.setSubCategory(legacyCategory);
            budgets.save(budget);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
