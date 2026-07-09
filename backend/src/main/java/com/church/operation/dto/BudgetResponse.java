package com.church.operation.dto;

import com.church.operation.entity.Budget;
import com.church.operation.util.BudgetType;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetResponse(
    String id,
    int fiscalYear,
    BudgetType budgetType,
    String category,
    String subCategory,
    BigDecimal budget,
    String memo,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt
) {
    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
            budget.getId(),
            budget.getFiscalYear(),
            budget.getBudgetType(),
            budget.getCategory(),
            budget.getSubCategory(),
            budget.getBudget(),
            budget.getMemo(),
            budget.getCreatedBy(),
            budget.getCreatedAt(),
            budget.getUpdatedBy(),
            budget.getUpdatedAt()
        );
    }
}
