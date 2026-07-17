package com.church.operation.dto;

import com.church.operation.util.GivingType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OfferingRequest(
    GivingType givingType,
    String memberId,
    String giverLabel,
    LocalDate offeringDate,
    LocalDate offeringSunday,
    String fundCode,
    String categoryCode,
    BigDecimal amount,
    String paymentMethod,
    String memo
) {
    public OfferingRequest(
        GivingType givingType,
        String memberId,
        String giverLabel,
        LocalDate offeringDate,
        LocalDate offeringSunday,
        String legacyFundCategory,
        BigDecimal amount,
        String paymentMethod,
        String memo
    ) {
        this(givingType, memberId, giverLabel, offeringDate, offeringSunday,
            "GENERAL", legacyFundCategory, amount, paymentMethod, memo);
    }
}
