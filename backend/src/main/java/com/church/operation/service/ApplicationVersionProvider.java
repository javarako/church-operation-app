package com.church.operation.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class ApplicationVersionProvider {
    private final ObjectProvider<BuildProperties> buildProperties;
    private final String fallbackVersion;

    public ApplicationVersionProvider(
        ObjectProvider<BuildProperties> buildProperties,
        @Value("${church.application-version:1.0.0-SNAPSHOT}") String fallbackVersion
    ) {
        this.buildProperties = buildProperties;
        this.fallbackVersion = fallbackVersion;
    }

    public String version() {
        BuildProperties available = buildProperties.getIfAvailable();
        return available == null ? fallbackVersion : available.getVersion();
    }
}
