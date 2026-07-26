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
    String source,
    Instant updatedAt
) {
}
