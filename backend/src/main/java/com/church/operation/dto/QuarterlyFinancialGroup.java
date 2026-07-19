package com.church.operation.dto;

import java.util.List;

public record QuarterlyFinancialGroup(
    int sequence,
    String groupCode,
    String groupLabel,
    List<QuarterlyFinancialRow> rows
) {
}
