package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
    long activeMemberCount,
    long newMemberCount,
    BigDecimal ytdOfferingActual,
    BigDecimal ytdOfferingBudget,
    BigDecimal ytdOfferingPercentage,
    BigDecimal ytdExpenseActual,
    BigDecimal ytdExpenseBudget,
    BigDecimal ytdExpensePercentage,
    long pendingChequeCount,
    BigDecimal pendingChequeTotal,
    BigDecimal weekOfferingTotal,
    BigDecimal monthOfferingTotal,
    BigDecimal yearOfferingTotal,
    LocalDate fiscalYearStart,
    LocalDate fiscalYearEnd,
    List<DashboardTrendPoint> offeringTrend
) {
}
