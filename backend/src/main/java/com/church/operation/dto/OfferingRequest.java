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
    String fundCategory,
    BigDecimal amount,
    String paymentMethod,
    String memo
) {}
