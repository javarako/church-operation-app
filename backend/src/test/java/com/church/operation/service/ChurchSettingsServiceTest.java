package com.church.operation.service;

import com.church.operation.dto.ChurchSettingsSaveRequest;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.entity.Member;
import com.church.operation.repo.ChurchSettingsRepository;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChurchSettingsServiceTest {
    @Mock private ChurchSettingsRepository repository;
    @Mock private ChurchInformationResolver resolver;
    @Mock private ChurchBrandingService branding;
    @Mock private SystemAuditService audit;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T18:00:00Z"), ZoneOffset.UTC);
    private ChurchSettingsService service;
    private Member admin;

    @BeforeEach
    void setUp() {
        service = new ChurchSettingsService(repository, resolver, branding, audit, clock);
        admin = member(Role.ADMIN);
    }

    @Test
    void savesTrimmedTextAndReplacesImagesWithoutDeletingOldFilesEarly() {
        ChurchSettings existing = existingSettings();
        MockMultipartFile logo = file("logo");
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(branding.store(logo, "logo"))
            .thenReturn(new ChurchBrandingService.StoredBranding("new-logo", "image/png"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resolver.resolve()).thenReturn(effective());

        var response = service.save(admin, request("  Runtime Church  ", " https://church.example.org "), logo, null);

        InOrder order = inOrder(branding, repository);
        order.verify(branding).store(logo, "logo");
        order.verify(repository).save(any());
        order.verify(branding).deleteQuietly("old-logo");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            "Runtime Church".equals(saved.getName())
                && "new-logo".equals(saved.getLogoGridFsId())
                && "old-banner".equals(saved.getBannerGridFsId())
                && clock.instant().equals(saved.getUpdatedAt())
        ));
        assertThat(response.source()).isEqualTo("DATABASE");
    }

    @Test
    void failedPersistenceDeletesOnlyNewlyUploadedFiles() {
        ChurchSettings existing = existingSettings();
        MockMultipartFile logo = file("logo");
        MockMultipartFile banner = file("banner");
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(branding.store(logo, "logo"))
            .thenReturn(new ChurchBrandingService.StoredBranding("new-logo", "image/png"));
        when(branding.store(banner, "banner"))
            .thenReturn(new ChurchBrandingService.StoredBranding("new-banner", "image/png"));
        when(repository.save(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.save(admin, request("Runtime Church", ""), logo, banner))
            .isInstanceOf(IllegalStateException.class);

        verify(branding).deleteQuietly("new-logo");
        verify(branding).deleteQuietly("new-banner");
        verify(branding, never()).deleteQuietly("old-logo");
        verify(branding, never()).deleteQuietly("old-banner");
        verify(audit).recordFailure(eq(admin), eq(SystemAuditOperation.CHURCH_SETTINGS_UPDATE), any(), any());
    }

    @Test
    void resetDeletesDocumentBeforeOldBranding() {
        ChurchSettings existing = existingSettings();
        when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(resolver.resolve()).thenReturn(effective());

        var response = service.reset(admin);

        InOrder order = inOrder(repository, branding);
        order.verify(repository).deleteById(ChurchSettings.SINGLETON_ID);
        order.verify(branding).deleteQuietly("old-logo");
        order.verify(branding).deleteQuietly("old-banner");
        assertThat(response.source()).isEqualTo("SERVER_DEFAULTS");
    }

    @Test
    void rejectsNonAdminAndInvalidRequiredOrWebsiteValues() {
        assertThatThrownBy(() -> service.get(member(Role.VIEWER)))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Administrator access is required.");
        assertThatThrownBy(() -> service.save(admin, request(" ", ""), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
        assertThatThrownBy(() -> service.save(admin, request("Church", "ftp://church.example.org"), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTP or HTTPS");
    }

    private ChurchSettings existingSettings() {
        ChurchSettings settings = new ChurchSettings();
        settings.setLogoGridFsId("old-logo");
        settings.setLogoContentType("image/png");
        settings.setBannerGridFsId("old-banner");
        settings.setBannerContentType("image/jpeg");
        settings.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        settings.setCreatedByMemberId("original-admin");
        return settings;
    }

    private ChurchSettingsSaveRequest request(String name, String website) {
        return new ChurchSettingsSaveRequest(
            name,
            "  123 Church Street  ",
            " contact@example.org ",
            " Treasurer ",
            " 123456789RR0001 ",
            " Toronto, Ontario ",
            website
        );
    }

    private EffectiveChurchInformation effective() {
        return new EffectiveChurchInformation(
            "Runtime Church", "123 Church Street", "contact@example.org", "Treasurer",
            "123456789RR0001", "Toronto, Ontario", "https://church.example.org",
            "/api/church-information/logo?v=1", "/api/church-information/banner?v=1", clock.instant()
        );
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile(name, name + ".png", "image/png", new byte[] {1});
    }

    private Member member(Role role) {
        Member member = new Member();
        member.setId(role.name().toLowerCase() + "-id");
        member.setPrimaryEmail(role.name().toLowerCase() + "@church.local");
        member.setRoles(Set.of(role));
        return member;
    }
}
