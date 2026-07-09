package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OfficialTaxReportRow(
    String churchName,
    String churchAddress,
    String churchContactInfo,
    String treasurerName,
    int taxYear,
    String memberId,
    String memberName,
    String primaryEmail,
    String offeringNumber,
    String memberAddress,
    LocalDate givingDate,
    String fundCategory,
    BigDecimal amount
) {
}
