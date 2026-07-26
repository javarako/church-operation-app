package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChurchSettingsSaveRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @Size(max = 500) String contactInfo,
    @Size(max = 200) String treasurerName,
    @Size(max = 200) String charityRegistrationNumber,
    @Size(max = 200) String receiptIssueLocation,
    @Size(max = 500) String website
) {
}
