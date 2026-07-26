package com.church.operation.rest;

import com.church.operation.dto.ChurchSettingsAdminResponse;
import com.church.operation.entity.Member;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.service.ChurchSettingsService;
import com.church.operation.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ChurchSettingsControllerTest {
    private final ChurchSettingsService service = mock(ChurchSettingsService.class);
    private MockMvc mockMvc;
    private Member admin;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ChurchSettingsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        admin = new Member();
        admin.setId("admin-1");
        admin.setRoles(Set.of(Role.ADMIN));
    }

    @Test
    void readsSavesMultipartSettingsAndResetsForAuthenticatedAdmin() throws Exception {
        when(service.get(admin)).thenReturn(response("DATABASE"));
        when(service.save(eq(admin), any(), any(), any())).thenReturn(response("DATABASE"));
        when(service.reset(admin)).thenReturn(response("SERVER_DEFAULTS"));

        mockMvc.perform(get("/api/admin/church-settings").principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Runtime Church"));

        MockMultipartFile settings = new MockMultipartFile(
            "settings", "", "application/json", requestJson().getBytes()
        );
        MockMultipartFile logo = new MockMultipartFile(
            "logo", "logo.png", "image/png", new byte[] {1}
        );
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/admin/church-settings")
                .file(settings).file(logo).principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("DATABASE"));
        verify(service).save(eq(admin), any(), any(), eq(null));

        mockMvc.perform(post("/api/admin/church-settings/reset").principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("SERVER_DEFAULTS"));
    }

    @Test
    void returnsForbiddenWhenServiceRejectsNonAdmin() throws Exception {
        when(service.get(any())).thenThrow(new SecurityException("Administrator access is required."));

        mockMvc.perform(get("/api/admin/church-settings").principal(authentication()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken(admin, null);
    }

    private ChurchSettingsAdminResponse response(String source) {
        return new ChurchSettingsAdminResponse(
            "Runtime Church", "123 Church Street", "contact@example.org", "Treasurer",
            "123456789RR0001", "Toronto, Ontario", "https://church.example.org",
            "/logo", "/banner", source, null
        );
    }

    private String requestJson() {
        return """
            {"name":"Runtime Church","address":"123 Church Street","contactInfo":"contact@example.org",
             "treasurerName":"Treasurer","charityRegistrationNumber":"123456789RR0001",
             "receiptIssueLocation":"Toronto, Ontario","website":"https://church.example.org"}
            """;
    }
}
