package com.church.operation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailSettingsTestRequest(
    @NotBlank @Size(max = 253) String host,
    @Min(1) @Max(65535) int port,
    @Size(max = 320) String username,
    @Size(max = 1024) String password,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @NotBlank @Email @Size(max = 320) String testRecipient
) {
}
