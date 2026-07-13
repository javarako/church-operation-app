package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "church.member-image")
public record MemberImageProperties(DataSize maxSize) {
    public MemberImageProperties {
        if (maxSize == null || maxSize.toBytes() <= 0) {
            throw new IllegalArgumentException("Member image maximum size must be positive.");
        }
    }
}
