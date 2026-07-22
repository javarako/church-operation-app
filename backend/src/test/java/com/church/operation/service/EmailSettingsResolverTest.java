package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.entity.EmailSettings;
import com.church.operation.exception.EmailConfigurationException;
import com.church.operation.repo.EmailSettingsRepository;
import com.church.operation.util.EmailSettingsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailSettingsResolverTest {
    @Mock private EmailSettingsRepository repository;

    private RuntimeEmailProperties properties;
    private EmailSettingsCrypto crypto;
    private EmailSettingsResolver resolver;

    @BeforeEach
    void setUp() {
        properties = properties(key());
        crypto = new EmailSettingsCrypto(properties);
        resolver = new EmailSettingsResolver(repository, crypto, properties);
    }

    @Test
    void usesServerDefaultsWhenNoOverrideExists() {
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        EffectiveEmailSettings result = resolver.resolve();

        assertThat(result.host()).isEqualTo("env.smtp.test");
        assertThat(result.port()).isEqualTo(587);
        assertThat(result.username()).isEqualTo("env-user");
        assertThat(result.password()).containsExactly("env-secret".toCharArray());
        assertThat(result.source()).isEqualTo(EmailSettingsSource.SERVER_DEFAULTS);
    }

    @Test
    void databaseOverrideWinsButAuthAndTlsRemainFromEnvironment() {
        EmailSettings saved = savedSettings("db.smtp.test", 2525, "db-user", "db-secret", "church@test.org");
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        EffectiveEmailSettings result = resolver.resolve();

        assertThat(result.host()).isEqualTo("db.smtp.test");
        assertThat(result.password()).containsExactly("db-secret".toCharArray());
        assertThat(result.auth()).isTrue();
        assertThat(result.startTls()).isTrue();
        assertThat(result.source()).isEqualTo(EmailSettingsSource.DATABASE);
    }

    @Test
    void emptyDraftPasswordPreservesDatabaseOrEnvironmentPassword() {
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        EffectiveEmailSettings result = resolver.resolveDraft(
            new EmailSettingsDraft("new.smtp.test", 2525, "new-user", new char[0], "from@test.org")
        );

        assertThat(result.host()).isEqualTo("new.smtp.test");
        assertThat(result.password()).containsExactly("env-secret".toCharArray());
        assertThat(result.source()).isEqualTo(EmailSettingsSource.DATABASE);
    }

    @Test
    void databaseDocumentWithoutOwnPasswordFallsBackToEnvironmentPassword() {
        EmailSettings saved = savedSettings("db.smtp.test", 2525, "db-user", null, "church@test.org");
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

        assertThat(resolver.resolve().password()).containsExactly("env-secret".toCharArray());
    }

    @Test
    void wrongKeyFailsOnlyWhenEncryptedPasswordIsResolved() {
        EmailSettings saved = savedSettings("db.smtp.test", 2525, "db-user", "db-secret", "church@test.org");
        when(repository.findById(EmailSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));
        EmailSettingsResolver wrong = new EmailSettingsResolver(
            repository, new EmailSettingsCrypto(properties(key())), properties(key())
        );

        assertThatThrownBy(wrong::resolve)
            .isInstanceOf(EmailConfigurationException.class)
            .hasMessage("The saved email password cannot be decrypted with this server configuration.");
    }

    private EmailSettings savedSettings(
        String host, int port, String username, String password, String fromAddress
    ) {
        EmailSettings saved = new EmailSettings();
        saved.setHost(host);
        saved.setPort(port);
        saved.setUsername(username);
        saved.setFromAddress(fromAddress);
        if (password != null) {
            var encrypted = crypto.encrypt(password.toCharArray());
            saved.setPasswordCiphertext(encrypted.ciphertext());
            saved.setPasswordNonce(encrypted.nonce());
            saved.setCipherVersion(encrypted.version());
        }
        return saved;
    }

    private RuntimeEmailProperties properties(String encryptionKey) {
        return new RuntimeEmailProperties(
            "env.smtp.test", 587, "env-user", "env-secret", true, true,
            "env-from@test.org", encryptionKey, Duration.ofMinutes(10)
        );
    }

    private String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
