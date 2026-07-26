package com.church.operation.service;

import java.time.Instant;

public record EffectiveChurchInformation(
    String name,
    String address,
    String contactInfo,
    String treasurerName,
    String charityRegistrationNumber,
    String receiptIssueLocation,
    String website,
    String logoUrl,
    String bannerUrl,
    Instant updatedAt
) {
}
