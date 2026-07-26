package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.service.ApplicationVersionProvider;
import com.church.operation.service.ChurchBrandingService;
import com.church.operation.service.ChurchInformationResolver;
import com.church.operation.service.EffectiveChurchInformation;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/church-information")
public class ChurchInformationController {
    private final ChurchInformationResolver resolver;
    private final ChurchBrandingService branding;
    private final ApplicationVersionProvider versionProvider;
    private final ChurchInformationProperties properties;

    public ChurchInformationController(
        ChurchInformationResolver resolver,
        ChurchBrandingService branding,
        ApplicationVersionProvider versionProvider,
        ChurchInformationProperties properties
    ) {
        this.resolver = resolver;
        this.branding = branding;
        this.versionProvider = versionProvider;
        this.properties = properties;
    }

    @GetMapping
    ChurchInformationResponse getChurchInformation() {
        EffectiveChurchInformation effective = resolver.resolve();
        return new ChurchInformationResponse(
            effective.name(),
            effective.address(),
            effective.contactInfo(),
            effective.treasurerName(),
            effective.charityRegistrationNumber(),
            effective.receiptIssueLocation(),
            effective.website(),
            effective.bannerUrl(),
            effective.logoUrl(),
            properties.ui().listPageSize(),
            versionProvider.version()
        );
    }

    @GetMapping("/logo")
    ResponseEntity<byte[]> getLogo() {
        ChurchSettings settings = resolver.savedSettings().orElse(null);
        return imageResponse(branding.effectiveLogo(settings));
    }

    @GetMapping("/banner")
    ResponseEntity<byte[]> getBanner() {
        ChurchSettings settings = resolver.savedSettings().orElse(null);
        return imageResponse(branding.effectiveBanner(settings));
    }

    private ResponseEntity<byte[]> imageResponse(ChurchBrandingService.BrandingContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(content.bytes());
    }

    record ChurchInformationResponse(
        String name,
        String address,
        String contactInfo,
        String treasurerName,
        String charityRegistrationNumber,
        String receiptIssueLocation,
        String website,
        String bannerPath,
        String logPath,
        int listPageSize,
        String applicationVersion
    ) {
    }
}
