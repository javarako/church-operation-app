package com.church.operation.dto;

import java.math.BigDecimal;

public record MemberOfferingSummaryReportRow(
    String memberId,
    String memberName,
    String primaryEmail,
    String offeringNumber,
    String fundCategory,
    long count,
    BigDecimal totalAmount
) {
}
