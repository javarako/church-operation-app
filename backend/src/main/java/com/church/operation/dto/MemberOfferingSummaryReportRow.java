package com.church.operation.dto;

import java.math.BigDecimal;

public record MemberOfferingSummaryReportRow(
    String memberId,
    String memberName,
    String primaryEmail,
    String offeringNumber,
    String fundCode,
    String categoryCode,
    long count,
    BigDecimal totalAmount
) {
    public MemberOfferingSummaryReportRow(
        String memberId,
        String memberName,
        String primaryEmail,
        String offeringNumber,
        String legacyFundCategory,
        long count,
        BigDecimal totalAmount
    ) {
        this(memberId, memberName, primaryEmail, offeringNumber, "GENERAL",
            legacyFundCategory, count, totalAmount);
    }

    public String fundCategory() {
        return categoryCode;
    }
}
