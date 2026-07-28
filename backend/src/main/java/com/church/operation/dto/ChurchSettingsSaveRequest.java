package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ChurchSettingsSaveRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @Size(max = 500) String contactInfo,
    @Size(max = 200) String treasurerName,
    @Size(max = 200) String charityRegistrationNumber,
    @Size(max = 200) String receiptIssueLocation,
    @Size(max = 500) String website,
    @NotBlank @Size(max = 100) String timeZone,
    @Min(1) @Max(12) int fiscalYearStartMonth,
    @Min(5) @Max(100) int listPageSize,
    @Min(1) @Max(120) long dataOperationExpiryMinutes
) {
}
