package com.church.operation.dto;

import com.church.operation.util.GivingType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyOfferingReportRow(
    LocalDate offeringSunday,
    String fundCode,
    String categoryCode,
    GivingType givingType,
    String paymentMethod,
    long count,
    BigDecimal totalAmount
) {
    public WeeklyOfferingReportRow(
        LocalDate offeringSunday,
        String legacyFundCategory,
        GivingType givingType,
        String paymentMethod,
        long count,
        BigDecimal totalAmount
    ) {
        this(offeringSunday, "GENERAL", legacyFundCategory, givingType, paymentMethod, count, totalAmount);
    }

    public String fundCategory() {
        return categoryCode;
    }
}
