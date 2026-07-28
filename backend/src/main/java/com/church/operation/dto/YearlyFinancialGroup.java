package com.church.operation.dto;

import java.util.List;

public record YearlyFinancialGroup(
    int sequence,
    String groupCode,
    String groupLabel,
    List<YearlyFinancialRow> rows
) {
}
