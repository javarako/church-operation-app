package com.church.operation.service;

import java.time.DateTimeException;
import java.time.LocalDate;

record YearlyFinancialPeriod(int fiscalYear, LocalDate fiscalStart, LocalDate fiscalEnd) {
    static YearlyFinancialPeriod from(int fiscalYear, int startMonth) {
        if (fiscalYear < 2000 || startMonth < 1 || startMonth > 12) {
            throw new IllegalArgumentException("A valid fiscal year is required.");
        }
        try {
            LocalDate start = LocalDate.of(fiscalYear, startMonth, 1);
            return new YearlyFinancialPeriod(fiscalYear, start, start.plusYears(1).minusDays(1));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("A valid fiscal year is required.", exception);
        }
    }
}
