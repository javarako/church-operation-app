package com.church.operation.dto;

import com.church.operation.util.BudgetType;

import java.math.BigDecimal;

public record FinancialBudgetReportRow(
    int fiscalYear,
    BudgetType budgetType,
    String category,
    String subCategory,
    BigDecimal budget,
    BigDecimal actual,
    BigDecimal variance
) {
}
