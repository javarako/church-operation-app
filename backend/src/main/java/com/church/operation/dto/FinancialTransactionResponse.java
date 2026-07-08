package com.church.operation.dto;

import com.church.operation.entity.FinancialTransaction;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinancialTransactionResponse(
    String id,
    FinancialTransactionType type,
    LocalDate transactionDate,
    BigDecimal amount,
    String category,
    String subCategory,
    boolean hstIncluded,
    String chequeNo,
    boolean chequeCleared,
    String payableTo,
    String treasurer,
    String memo,
    FinancialSourceType sourceType,
    String sourceId,
    String createdBy,
    Instant createdAt
) {
    public static FinancialTransactionResponse from(FinancialTransaction transaction) {
        return new FinancialTransactionResponse(
            transaction.getId(),
            transaction.getType(),
            transaction.getTransactionDate(),
            transaction.getAmount(),
            transaction.getCategory(),
            transaction.getSubCategory(),
            transaction.isHstIncluded(),
            transaction.getChequeNo(),
            transaction.isChequeCleared(),
            transaction.getPayableTo(),
            transaction.getTreasurer(),
            transaction.getMemo(),
            transaction.getSourceType(),
            transaction.getSourceId(),
            transaction.getCreatedBy(),
            transaction.getCreatedAt()
        );
    }
}
