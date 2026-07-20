package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record YearlyFinancialReport(
    int fiscalYear,
    LocalDate fiscalStart,
    LocalDate fiscalEnd,
    List<YearlyFinancialGroup> groups,
    BigDecimal specialCurrentBudget,
    BigDecimal specialActual,
    BigDecimal specialNextBudget,
    boolean specialNextBudgetPresent,
    String sheetName,
    String titleSuffix,
    String actualHeader,
    String actualRatioHeader,
    String specialRowLabel
) {
}
