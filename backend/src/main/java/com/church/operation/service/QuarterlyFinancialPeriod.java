package com.church.operation.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record QuarterlyFinancialPeriod(
    int calendarYear,
    int quarter,
    LocalDate quarterStart,
    LocalDate quarterEnd,
    int fiscalBudgetYear,
    LocalDate fiscalStart,
    List<YearMonth> months
) {
    public static QuarterlyFinancialPeriod from(int year, int quarter, int fiscalStartMonth) {
        if (year < 2000 || quarter < 1 || quarter > 4
            || fiscalStartMonth < 1 || fiscalStartMonth > 12) {
            throw new IllegalArgumentException("A valid calendar year and quarter are required.");
        }

        int firstMonth = ((quarter - 1) * 3) + 1;
        LocalDate quarterStart = LocalDate.of(year, firstMonth, 1);
        LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
        int fiscalBudgetYear = quarterEnd.getMonthValue() >= fiscalStartMonth
            ? quarterEnd.getYear()
            : quarterEnd.getYear() - 1;
        LocalDate fiscalStart = LocalDate.of(fiscalBudgetYear, fiscalStartMonth, 1);
        return new QuarterlyFinancialPeriod(
            year,
            quarter,
            quarterStart,
            quarterEnd,
            fiscalBudgetYear,
            fiscalStart,
            List.of(
                YearMonth.from(quarterStart),
                YearMonth.from(quarterStart.plusMonths(1)),
                YearMonth.from(quarterStart.plusMonths(2))
            )
        );
    }
}
