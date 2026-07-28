package com.church.operation.dto;

import java.time.Instant;

public record ChurchSettingsAdminResponse(
    String name,
    String address,
    String contactInfo,
    String treasurerName,
    String charityRegistrationNumber,
    String receiptIssueLocation,
    String website,
    String logoUrl,
    String bannerUrl,
    String timeZone,
    int fiscalYearStartMonth,
    int listPageSize,
    long dataOperationExpiryMinutes,
    String source,
    Instant updatedAt
) {
}
