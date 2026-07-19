package com.church.operation.util;

public enum ReferenceDataType {
    GROUP_CODE,
    MEMBERSHIP_STATUS,
    COMMITTEE_CODE,
    OFFERING_FUND,
    OFFERING_CATEGORY,
    // Retained so legacy MongoDB documents can be migrated safely.
    OFFERING_FUND_CATEGORY,
    PAYMENT_METHOD,
    FINANCIAL_CATEGORY,
    FINANCIAL_SUB_CATEGORY
}
