package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record QuarterlyFinancialReport(
    int calendarYear,
    int quarter,
    LocalDate quarterStart,
    LocalDate quarterEnd,
    int fiscalBudgetYear,
    LocalDate fiscalStart,
    List<YearMonth> months,
    List<QuarterlyFinancialGroup> groups,
    BigDecimal specialBudget,
    List<BigDecimal> specialMonthlyActuals,
    BigDecimal specialCumulativeActual,
    String sheetName,
    String titleSuffix,
    String specialRowLabel
) {
}
