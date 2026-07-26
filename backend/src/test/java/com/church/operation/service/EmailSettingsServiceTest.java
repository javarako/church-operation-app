package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.dto.EmailSettingsResetRequest;
import com.church.operation.dto.EmailSettingsSaveRequest;
import com.church.operation.dto.EmailSettingsTestRequest;
import com.church.operation.entity.EmailSettings;
import com.church.operation.entity.Member;
import com.church.operation.exception.EmailDeliveryException;
import com.church.operation.repo.EmailSettingsRepository;
import com.church.operation.util.EmailSettingsSource;
import com.church.operation.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailSettingsServiceTest {
    @Mock private EmailSettingsRepository repository;
    @Mock private EmailSettingsResolver resolver;
    @Mock private EmailSettingsCrypto crypto;
    @Mock private RuntimeEmailSender sender;
    @Mock private EmailSettingsVerificationStore verificationStore;
    @Mock private SystemAuditService audit;

    private RuntimeEmailProperties properties;
    private EmailSettingsService service;
    private Member admin;

    @BeforeEach
    void setUp() {
        properties = new RuntimeEmailProperties(
            "env.smtp.test", 1025, "", "", false, false,
            "env-from@test.org", "key", Duration.ofMinutes(10)
        );
        service = new EmailSettingsService(
            repository, resolver, crypto, sender, verificationStore, properties,
            Clock.fixed(Instant.parse("2026-07-22T15:00:00Z"), ZoneOffset.UTC)
        );
        admin = new Member();
        admin.setId("admin-1");
        admin.setPrimaryEmail("admin@example.org");
        admin.setRoles(Set.of(Role.ADMIN));
    }

    @Test
    void springCanConstructTheEmailSettingsServiceBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmailSettingsRepository.class, () -> repository);
            context.registerBean(EmailSettingsResolver.class, () -> resolver);
            context.registerBean(EmailSettingsCrypto.class, () -> crypto);
            context.registerBean(RuntimeEmailSender.class, () -> sender);
            context.registerBean(EmailSettingsVerificationStore.class, () -> verificationStore);
            context.registerBean(RuntimeEmailProperties.class, () -> properties);
            context.registerBean(SystemAuditService.class, () -> audit);
            context.register(EmailSettingsService.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(EmailSettingsService.class)).isNotNull();
        }
    }

    @Test
    void successfulTestDoesNotPersistAndReturnsVerificationToken() {
        EffectiveEmailSettings effective = effective("smtp.test", "new-secret", EmailSettingsSource.DATABASE);
        when(resolver.resolveDraft(any())).thenReturn(effective);
        when(crypto.fingerprint(anyString())).thenReturn(new byte[] {1});
        when(verificationStore.remember(eq("admin-1"), any())).thenReturn(
            new EmailSettingsVerificationStore.VerificationToken("token-1", Instant.parse("2026-07-22T15:10:00Z"))
        );

        var response = service.test(admin, testRequest("new-secret"));

        verify(sender).send(eq(effective), eq("admin@example.org"), anyString(), anyString());
        verify(repository, never()).save(any());
        assertThat(response.verificationToken()).isEqualTo("token-1");
    }

    @Test
    void saveRequiresMatchingTestAndEncryptsChangedPassword() {
        EffectiveEmailSettings effective = effective("smtp.test", "new-secret", EmailSettingsSource.DATABASE);
        when(resolver.resolveDraft(any())).thenReturn(effective);
        when(crypto.fingerprint(anyString())).thenReturn(new byte[] {1});
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(crypto.encrypt(any())).thenReturn(new EmailSettingsCrypto.EncryptedSecret("cipher", "nonce", 1));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(admin, new EmailSettingsSaveRequest(
            "smtp.test", 587, "user", "new-secret", "from@test.org", "token-1"
        ));

        verify(verificationStore).consume(eq("token-1"), eq("admin-1"), any());
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            "cipher".equals(saved.getPasswordCiphertext()) && !saved.getPasswordCiphertext().contains("new-secret")
        ));
        assertThat(response.passwordConfigured()).isTrue();
        assertThat(response.source()).isEqualTo(EmailSettingsSource.DATABASE);
    }

    @Test
    void blankPasswordPreservesStoredCiphertext() {
        EmailSettings existing = new EmailSettings();
        existing.setPasswordCiphertext("existing-cipher");
        existing.setPasswordNonce("existing-nonce");
        existing.setCipherVersion(1);
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(resolver.resolveDraft(any())).thenReturn(effective("smtp.test", "existing", EmailSettingsSource.DATABASE));
        when(crypto.fingerprint(anyString())).thenReturn(new byte[] {1});
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(admin, new EmailSettingsSaveRequest(
            "smtp.test", 587, "user", "", "from@test.org", "token-1"
        ));

        verify(crypto, never()).encrypt(any());
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            "existing-cipher".equals(saved.getPasswordCiphertext())
        ));
    }

    @Test
    void failedDefaultTestKeepsDatabaseOverride() {
        EffectiveEmailSettings defaults = effective("env.smtp.test", "", EmailSettingsSource.SERVER_DEFAULTS);
        when(resolver.serverDefaults()).thenReturn(defaults);
        doThrow(new EmailDeliveryException(EmailDeliveryException.Category.CONNECTION))
            .when(sender).send(eq(defaults), eq("admin@example.org"), anyString(), anyString());

        assertThatThrownBy(() -> service.reset(admin, new EmailSettingsResetRequest("admin@example.org")))
            .isInstanceOf(EmailDeliveryException.class);
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void rejectsNonAdmin() {
        Member viewer = new Member();
        viewer.setRoles(Set.of(Role.VIEWER));
        assertThatThrownBy(() -> service.get(viewer))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Administrator access is required.");
    }

    private EmailSettingsTestRequest testRequest(String password) {
        return new EmailSettingsTestRequest(
            "smtp.test", 587, "user", password, "from@test.org", "admin@example.org"
        );
    }

    private EffectiveEmailSettings effective(String host, String password, EmailSettingsSource source) {
        return new EffectiveEmailSettings(
            host, 587, "user", password.toCharArray(), false, false, "from@test.org", source
        );
    }
}
