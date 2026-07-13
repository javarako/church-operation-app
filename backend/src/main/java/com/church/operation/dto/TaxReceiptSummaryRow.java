package com.church.operation.dto;

import com.church.operation.util.TaxReceiptStatus;

import java.math.BigDecimal;

public record TaxReceiptSummaryRow(
    String memberId,
    String offeringNumber,
    String donorName,
    String donorAddress,
    int taxYear,
    BigDecimal totalAmount,
    String receiptId,
    String receiptNumber,
    TaxReceiptStatus receiptStatus,
    boolean sourceChanged
) {
}
