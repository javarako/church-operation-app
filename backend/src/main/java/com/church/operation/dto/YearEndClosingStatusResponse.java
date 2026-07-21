package com.church.operation.dto;

import java.time.LocalDate;

public record YearEndClosingStatusResponse(
    int fiscalYear,
    LocalDate fiscalEndDate,
    boolean closeEligible,
    YearEndClosingReportStatus offering,
    YearEndClosingReportStatus expenditure
) {
}
