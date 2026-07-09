package com.church.operation.service;

import com.church.operation.dto.BudgetRequest;
import com.church.operation.entity.Budget;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final ReferenceDataRepository referenceDataRepository;

    public BudgetService(BudgetRepository budgetRepository, ReferenceDataRepository referenceDataRepository) {
        this.budgetRepository = budgetRepository;
        this.referenceDataRepository = referenceDataRepository;
    }

    public List<Budget> listBudgets(Member actor, int fiscalYear) {
        requireBudgetAccess(actor);
        validateFiscalYear(fiscalYear);
        return budgetRepository.findActiveByFiscalYear(fiscalYear);
    }

    public Budget createBudget(Member actor, BudgetRequest request) {
        requireBudgetAccess(actor);
        validateRequest(request);

        Budget budget = new Budget();
        budget.setCreatedBy(actor.getId());
        budget.setCreatedAt(Instant.now());
        applyBudgetFields(budget, request);
        return budgetRepository.save(budget);
    }

    public Budget updateBudget(Member actor, String id, BudgetRequest request) {
        requireBudgetAccess(actor);
        validateRequest(request);

        Budget budget = findActiveBudget(id);
        applyBudgetFields(budget, request);
        budget.setUpdatedBy(actor.getId());
        budget.setUpdatedAt(Instant.now());
        return budgetRepository.save(budget);
    }

    public Budget deleteBudget(Member actor, String id) {
        requireBudgetAccess(actor);
        Budget budget = findActiveBudget(id);
        budget.setDeleted(true);
        budget.setDeletedBy(actor.getId());
        budget.setDeletedAt(Instant.now());
        return budgetRepository.save(budget);
    }

    private Budget findActiveBudget(String id) {
        return budgetRepository.findById(id)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new IllegalArgumentException("Budget was not found."));
    }

    private void applyBudgetFields(Budget budget, BudgetRequest request) {
        budget.setFiscalYear(request.fiscalYear());
        budget.setBudgetType(request.budgetType());
        budget.setBudget(request.budget());
        budget.setMemo(trimToNull(request.memo()));

        if (request.budgetType() == BudgetType.CARRY_OVER) {
            budget.setCategory(null);
            budget.setSubCategory(null);
            ensureUniqueCarryOver(request.fiscalYear(), budget.getId());
            return;
        }

        String normalizedCategory = normalizeCategory(request.category());
        if (request.budgetType() == BudgetType.OFFERING_INCOME) {
            referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, normalizedCategory)
                .filter(ReferenceData::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Offering fund/category was not found."));
            budget.setCategory(normalizedCategory);
            budget.setSubCategory(null);
            ensureUniqueBudget(request.fiscalYear(), BudgetType.OFFERING_INCOME, normalizedCategory, null, budget.getId());
            return;
        }

        referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, normalizedCategory)
            .filter(ReferenceData::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Financial category was not found."));
        String normalizedSubCategory = normalizeSubCategory(normalizedCategory, request.subCategory());
        budget.setCategory(normalizedCategory);
        budget.setSubCategory(normalizedSubCategory);
        ensureUniqueBudget(request.fiscalYear(), BudgetType.EXPENSE, normalizedCategory, normalizedSubCategory, budget.getId());
    }

    private void validateRequest(BudgetRequest request) {
        validateFiscalYear(request.fiscalYear());
        if (request.budgetType() == null) {
            throw new IllegalArgumentException("Budget type is required.");
        }
        if (request.budget() == null || request.budget().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Budget must be zero or greater.");
        }
        if (request.budgetType() != BudgetType.CARRY_OVER && trimToNull(request.category()) == null) {
            throw new IllegalArgumentException("Budget category is required.");
        }
    }

    private void validateFiscalYear(int fiscalYear) {
        if (fiscalYear == 0) {
            throw new IllegalArgumentException("Fiscal year is required.");
        }
        if (fiscalYear < 2000 || fiscalYear > 2100) {
            throw new IllegalArgumentException("Fiscal year must be between 2000 and 2100.");
        }
    }

    private String normalizeCategory(String category) {
        String normalized = trimToNull(category);
        if (normalized == null) {
            throw new IllegalArgumentException("Budget category is required.");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeSubCategory(String category, String subCategory) {
        String normalized = trimToNull(subCategory);
        if (normalized == null) {
            return null;
        }
        String normalizedSubCategory = normalized.toUpperCase(Locale.ROOT);
        referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, normalizedSubCategory)
            .filter(ReferenceData::isActive)
            .filter(referenceData -> category.equals(referenceData.getParentCode()))
            .orElseThrow(() -> new IllegalArgumentException("Financial sub-category was not found for the selected category."));
        return normalizedSubCategory;
    }

    private void ensureUniqueCarryOver(int fiscalYear, String currentBudgetId) {
        boolean duplicateExists = budgetRepository.findActiveCarryOver(fiscalYear).stream()
            .anyMatch(existing -> !sameBudget(existing.getId(), currentBudgetId));
        if (duplicateExists) {
            throw new IllegalArgumentException("Carry-over budget already exists for this fiscal year.");
        }
    }

    private void ensureUniqueBudget(int fiscalYear, BudgetType budgetType, String category, String subCategory, String currentBudgetId) {
        boolean duplicateExists = budgetRepository.findActiveDuplicates(fiscalYear, budgetType, category, subCategory).stream()
            .anyMatch(existing -> !sameBudget(existing.getId(), currentBudgetId));
        if (duplicateExists) {
            throw new IllegalArgumentException("Budget already exists for this fiscal year and category.");
        }
    }

    private boolean sameBudget(String leftId, String rightId) {
        return leftId != null && leftId.equals(rightId);
    }

    private void requireBudgetAccess(Member actor) {
        if (hasRole(actor, Role.ADMIN) || hasRole(actor, Role.TREASURER)) {
            return;
        }
        throw new SecurityException("You do not have permission to manage budgets.");
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
