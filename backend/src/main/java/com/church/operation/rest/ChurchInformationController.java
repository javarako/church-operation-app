package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/church-information")
public class ChurchInformationController {
    private final ChurchInformationProperties properties;

    public ChurchInformationController(ChurchInformationProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    ChurchInformationResponse getChurchInformation() {
        return new ChurchInformationResponse(
            properties.information().name(),
            properties.information().address(),
            properties.information().contactInfo(),
            properties.information().treasurerName(),
            properties.information().charityRegistrationNumber(),
            properties.information().receiptIssueLocation(),
            properties.information().website(),
            properties.branding().bannerPath(),
            properties.branding().logPath(),
            properties.ui().listPageSize()
        );
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
        int listPageSize
    ) {
    }
}
