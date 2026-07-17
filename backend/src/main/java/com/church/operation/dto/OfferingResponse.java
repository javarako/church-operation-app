package com.church.operation.dto;

import com.church.operation.entity.Offering;
import com.church.operation.util.GivingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OfferingResponse(
    String id,
    GivingType givingType,
    String memberId,
    String giverLabel,
    String giverDisplayName,
    LocalDate offeringDate,
    LocalDate offeringSunday,
    String fundCode,
    String categoryCode,
    String fundCategory,
    BigDecimal amount,
    String paymentMethod,
    String memo,
    String incomeTransactionId,
    String createdBy,
    Instant createdAt
) {
    public static OfferingResponse from(Offering offering) {
        return new OfferingResponse(
            offering.getId(),
            offering.getGivingType(),
            offering.getMemberId(),
            offering.getGiverLabel(),
            offering.getGiverDisplayName(),
            offering.getOfferingDate(),
            offering.getOfferingSunday(),
            offering.getFundCode(),
            offering.getCategoryCode(),
            offering.getFundCategory(),
            offering.getAmount(),
            offering.getPaymentMethod(),
            offering.getMemo(),
            offering.getIncomeTransactionId(),
            offering.getCreatedBy(),
            offering.getCreatedAt()
        );
    }
}
