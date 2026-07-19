package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.Member;
import com.church.operation.service.AuthTokenService;
import com.church.operation.service.MaintenanceModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChurchInformationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChurchInformationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsConfiguredChurchInformation() throws Exception {
        mockMvc.perform(get("/api/church-information"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("church name --- IGNORE ---"))
            .andExpect(jsonPath("$.address").value("church address --- IGNORE ---"))
            .andExpect(jsonPath("$.contactInfo").value("contact info --- IGNORE ---"))
            .andExpect(jsonPath("$.treasurerName").value("treasurer name --- IGNORE ---"))
            .andExpect(jsonPath("$.charityRegistrationNumber").value(""))
            .andExpect(jsonPath("$.receiptIssueLocation").value(""))
            .andExpect(jsonPath("$.website").value(""))
            .andExpect(jsonPath("$.bannerPath").value("/branding/church-banner.png"))
            .andExpect(jsonPath("$.logPath").value("/branding/church_logo.png"))
            .andExpect(jsonPath("$.listPageSize").value(20));
    }

    @TestConfiguration
    @EnableConfigurationProperties(ChurchInformationProperties.class)
    static class TestConfig {
        @Bean
        ChurchInformationController churchInformationController(ChurchInformationProperties properties) {
            return new ChurchInformationController(properties);
        }

        @Bean
        AuthTokenService authTokenService() {
            return new AuthTokenService(null) {
                @Override
                public Optional<Member> findMember(String token) {
                    return Optional.empty();
                }
            };
        }

        @Bean
        MaintenanceModeService maintenanceModeService() {
            return new MaintenanceModeService();
        }
    }
}
