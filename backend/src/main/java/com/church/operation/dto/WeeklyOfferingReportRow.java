package com.church.operation.dto;

import com.church.operation.util.GivingType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyOfferingReportRow(
    LocalDate offeringSunday,
    String fundCategory,
    GivingType givingType,
    String paymentMethod,
    long count,
    BigDecimal totalAmount
) {
}
