package com.church.operation.rest;

import com.church.operation.dto.EmailSettingsResponse;
import com.church.operation.dto.EmailSettingsTestResponse;
import com.church.operation.entity.Member;
import com.church.operation.exception.EmailDeliveryException;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.service.EmailSettingsService;
import com.church.operation.util.EmailSettingsSource;
import com.church.operation.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class EmailSettingsControllerTest {
    private final EmailSettingsService service = mock(EmailSettingsService.class);
    private MockMvc mockMvc;
    private Member admin;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new EmailSettingsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        admin = new Member();
        admin.setId("admin-1");
        admin.setPrimaryEmail("admin@example.org");
        admin.setRoles(Set.of(Role.ADMIN));
    }

    @Test
    void readsTestsSavesAndResetsWithoutReturningPasswordMaterial() throws Exception {
        EmailSettingsResponse response = new EmailSettingsResponse(
            "smtp.test", 587, "user", "from@test.org", true,
            EmailSettingsSource.DATABASE, Instant.parse("2026-07-22T15:00:00Z")
        );
        when(service.get(admin)).thenReturn(response);
        when(service.test(any(), any())).thenReturn(new EmailSettingsTestResponse(
            "verification", Instant.parse("2026-07-22T15:10:00Z"), "Test email sent successfully."
        ));
        when(service.save(any(), any())).thenReturn(response);
        when(service.reset(any(), any())).thenReturn(new EmailSettingsResponse(
            "localhost", 1025, "", "from@test.org", false,
            EmailSettingsSource.SERVER_DEFAULTS, null
        ));

        mockMvc.perform(get("/api/admin/email-settings").principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordConfigured").value(true))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordCiphertext").doesNotExist());

        mockMvc.perform(post("/api/admin/email-settings/test").principal(authentication())
                .contentType("application/json").content(testJson("super-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationToken").value("verification"))
            .andExpect(content().string(not(containsString("super-secret"))));

        mockMvc.perform(put("/api/admin/email-settings").principal(authentication())
                .contentType("application/json").content(saveJson("super-secret")))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("super-secret"))));

        mockMvc.perform(post("/api/admin/email-settings/reset").principal(authentication())
                .contentType("application/json").content("{\"testRecipient\":\"admin@example.org\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("SERVER_DEFAULTS"));
    }

    @Test
    void sanitizesProviderFailure() throws Exception {
        when(service.test(any(), any())).thenThrow(
            new EmailDeliveryException(EmailDeliveryException.Category.AUTHENTICATION)
        );

        mockMvc.perform(post("/api/admin/email-settings/test").principal(authentication())
                .contentType("application/json").content(testJson("super-secret")))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_ERROR"))
            .andExpect(content().string(not(containsString("super-secret"))));
    }

    @Test
    void validatesBoundedEmailSettingsRequest() throws Exception {
        mockMvc.perform(post("/api/admin/email-settings/test").principal(authentication())
                .contentType("application/json")
                .content("{\"host\":\"\",\"port\":70000,\"fromAddress\":\"bad\",\"testRecipient\":\"bad\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken(admin, null);
    }

    private String testJson(String password) {
        return """
            {"host":"smtp.test","port":587,"username":"user","password":"%s",
             "fromAddress":"from@test.org","testRecipient":"admin@example.org"}
            """.formatted(password);
    }

    private String saveJson(String password) {
        return """
            {"host":"smtp.test","port":587,"username":"user","password":"%s",
             "fromAddress":"from@test.org","verificationToken":"verification"}
            """.formatted(password);
    }
}
