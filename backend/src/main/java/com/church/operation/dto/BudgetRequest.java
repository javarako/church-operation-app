package com.church.operation.dto;

import com.church.operation.util.BudgetType;

import java.math.BigDecimal;

public record BudgetRequest(
    int fiscalYear,
    BudgetType budgetType,
    String category,
    String subCategory,
    BigDecimal budget,
    String memo
) {
}
