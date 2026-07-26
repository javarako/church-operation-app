package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.service.ApplicationVersionProvider;
import com.church.operation.service.ChurchBrandingService;
import com.church.operation.service.ChurchInformationResolver;
import com.church.operation.service.EffectiveChurchInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ChurchInformationControllerTest {
    private final ChurchInformationResolver resolver = mock(ChurchInformationResolver.class);
    private final ChurchBrandingService branding = mock(ChurchBrandingService.class);
    private final ApplicationVersionProvider versionProvider = mock(ApplicationVersionProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information("", "", "", "", "", "", ""),
            new ChurchInformationProperties.Branding("/banner.png", "/logo.png"),
            new ChurchInformationProperties.Ui(20)
        );
        mockMvc = standaloneSetup(new ChurchInformationController(
            resolver, branding, versionProvider, properties
        )).build();
    }

    @Test
    void returnsEffectiveChurchInformationAndApplicationVersion() throws Exception {
        when(resolver.resolve()).thenReturn(effective());
        when(versionProvider.version()).thenReturn("1.0.0-SNAPSHOT");

        mockMvc.perform(get("/api/church-information"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Runtime Church"))
            .andExpect(jsonPath("$.address").value("123 Church Street"))
            .andExpect(jsonPath("$.contactInfo").value("contact@example.org"))
            .andExpect(jsonPath("$.treasurerName").value("Treasurer"))
            .andExpect(jsonPath("$.charityRegistrationNumber").value("123456789RR0001"))
            .andExpect(jsonPath("$.receiptIssueLocation").value("Toronto, Ontario"))
            .andExpect(jsonPath("$.website").value("https://church.example.org"))
            .andExpect(jsonPath("$.bannerPath").value("/api/church-information/banner?v=1"))
            .andExpect(jsonPath("$.logPath").value("/api/church-information/logo?v=1"))
            .andExpect(jsonPath("$.listPageSize").value(20))
            .andExpect(jsonPath("$.applicationVersion").value("1.0.0-SNAPSHOT"));
    }

    @Test
    void servesEffectiveBrandingWithContentTypeAndCacheHeader() throws Exception {
        ChurchSettings settings = new ChurchSettings();
        settings.setLogoGridFsId("logo-id");
        settings.setBannerGridFsId("banner-id");
        when(resolver.savedSettings()).thenReturn(Optional.of(settings));
        when(branding.effectiveLogo(settings))
            .thenReturn(new ChurchBrandingService.BrandingContent(new byte[] {1, 2}, "image/png"));
        when(branding.effectiveBanner(settings))
            .thenReturn(new ChurchBrandingService.BrandingContent(new byte[] {3, 4}, "image/jpeg"));

        mockMvc.perform(get("/api/church-information/logo"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Cache-Control", allOf(
                containsString("public"), containsString("max-age=3600")
            )))
            .andExpect(content().bytes(new byte[] {1, 2}));

        mockMvc.perform(get("/api/church-information/banner"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(header().string("Cache-Control", allOf(
                containsString("public"), containsString("max-age=3600")
            )))
            .andExpect(content().bytes(new byte[] {3, 4}));
    }

    private EffectiveChurchInformation effective() {
        return new EffectiveChurchInformation(
            "Runtime Church", "123 Church Street", "contact@example.org", "Treasurer",
            "123456789RR0001", "Toronto, Ontario", "https://church.example.org",
            "/api/church-information/logo?v=1", "/api/church-information/banner?v=1",
            Instant.parse("2026-07-26T18:00:00Z")
        );
    }
}
