package com.church.operation.service;

import com.church.operation.util.YearEndClosingStatus;

import java.time.Instant;

public record YearlyWorkbookLifecycle(
    YearEndClosingStatus status,
    Instant eventAt,
    Integer version
) {
    public static YearlyWorkbookLifecycle notClosed() {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.NOT_CLOSED, null, null);
    }

    public static YearlyWorkbookLifecycle reopened(Instant reopenedAt) {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.REOPENED, reopenedAt, null);
    }

    public static YearlyWorkbookLifecycle closed(Instant closedAt, int version) {
        return new YearlyWorkbookLifecycle(YearEndClosingStatus.CLOSED, closedAt, version);
    }

    public boolean finalized() {
        return status == YearEndClosingStatus.CLOSED;
    }
}
