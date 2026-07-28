package com.church.operation.dto;

import java.math.BigDecimal;

public record YearlyFinancialRow(
    String groupCode,
    String groupLabel,
    String itemCode,
    String itemLabel,
    BigDecimal currentBudget,
    BigDecimal actual,
    BigDecimal nextBudget,
    boolean nextBudgetPresent
) {
}
