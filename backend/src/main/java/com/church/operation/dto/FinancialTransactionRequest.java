package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionRequest(
    LocalDate transactionDate,
    BigDecimal amount,
    String category,
    String subCategory,
    boolean hstIncluded,
    String chequeNo,
    boolean chequeCleared,
    String payableTo,
    String treasurer,
    String memo
) {
}
