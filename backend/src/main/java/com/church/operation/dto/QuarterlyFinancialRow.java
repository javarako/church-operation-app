package com.church.operation.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuarterlyFinancialRow(
    String groupCode,
    String groupLabel,
    String itemCode,
    String itemLabel,
    BigDecimal budget,
    List<BigDecimal> monthlyActuals,
    BigDecimal cumulativeActual
) {
}
