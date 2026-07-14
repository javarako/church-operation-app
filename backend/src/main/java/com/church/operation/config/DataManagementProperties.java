package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "church.data-management")
public record DataManagementProperties(
    Path tempDirectory,
    Duration operationExpiry,
    DataSize maxUploadSize
) {
    public DataManagementProperties {
        if (tempDirectory == null) {
            throw new IllegalArgumentException("Data-management temporary directory is required.");
        }
        if (operationExpiry == null || operationExpiry.isZero() || operationExpiry.isNegative()) {
            throw new IllegalArgumentException("Data-management operation expiry must be positive.");
        }
        if (maxUploadSize == null || maxUploadSize.toBytes() <= 0) {
            throw new IllegalArgumentException("Data-management maximum upload size must be positive.");
        }
    }
}
