package com.church.operation.dto;

import java.time.LocalDate;

public record FiscalArchivePreview(
    int fiscalYear,
    LocalDate startDate,
    LocalDate endDate,
    long offeringCount,
    long linkedIncomeCount,
    long expenseCount,
    long budgetCount,
    long totalRecordCount
) {
}
