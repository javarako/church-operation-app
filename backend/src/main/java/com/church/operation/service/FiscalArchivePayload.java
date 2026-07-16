package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Offering;

import java.time.LocalDate;
import java.util.List;

public record FiscalArchivePayload(
    String archiveId,
    int fiscalYear,
    LocalDate startDate,
    LocalDate endDate,
    List<Offering> offerings,
    List<FinancialTransaction> linkedIncome,
    List<FinancialTransaction> expenses,
    List<Budget> budgets
) {
}
