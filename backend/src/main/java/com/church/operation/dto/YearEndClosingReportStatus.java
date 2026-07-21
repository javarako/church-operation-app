package com.church.operation.dto;

import com.church.operation.util.YearEndClosingStatus;
import com.church.operation.util.YearEndReportType;

import java.time.Instant;

public record YearEndClosingReportStatus(
    YearEndReportType reportType,
    YearEndClosingStatus status,
    Integer version,
    Instant eventAt
) {
}
