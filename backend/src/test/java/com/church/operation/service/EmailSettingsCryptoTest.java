package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.exception.EmailConfigurationException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSettingsCryptoTest {
    @Test
    void encryptsWithFreshNoncesAndRejectsTampering() {
        EmailSettingsCrypto crypto = crypto(key());
        var first = crypto.encrypt("smtp-secret".toCharArray());
        var second = crypto.encrypt("smtp-secret".toCharArray());

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(crypto.decrypt(first)).containsExactly("smtp-secret".toCharArray());

        byte[] changed = Base64.getDecoder().decode(first.ciphertext());
        changed[0] ^= 1;
        var tampered = new EmailSettingsCrypto.EncryptedSecret(
            Base64.getEncoder().encodeToString(changed), first.nonce(), first.version()
        );
        assertThatThrownBy(() -> crypto.decrypt(tampered))
            .isInstanceOf(EmailConfigurationException.class)
            .hasMessage("The saved email password cannot be decrypted with this server configuration.");
    }

    @Test
    void rejectsMissingMalformedAndWrongLengthKeysOnlyWhenCryptoIsUsed() {
        for (String invalid : new String[] {"", "not-base64", Base64.getEncoder().encodeToString(new byte[16])}) {
            EmailSettingsCrypto crypto = crypto(invalid);
            assertThatThrownBy(() -> crypto.encrypt("smtp-secret".toCharArray()))
                .isInstanceOf(EmailConfigurationException.class)
                .hasMessage("CHURCH_SETTINGS_ENCRYPTION_KEY must be a Base64-encoded 256-bit key.");
        }
    }

    @Test
    void wrongKeyCannotDecryptAndFingerprintsAreStable() {
        EmailSettingsCrypto first = crypto(key());
        EmailSettingsCrypto second = crypto(key());
        var encrypted = first.encrypt("smtp-secret".toCharArray());

        assertThatThrownBy(() -> second.decrypt(encrypted))
            .isInstanceOf(EmailConfigurationException.class)
            .hasMessage("The saved email password cannot be decrypted with this server configuration.");
        assertThat(first.fingerprint("settings")).isEqualTo(first.fingerprint("settings"));
        assertThat(first.fingerprint("settings")).isNotEqualTo(first.fingerprint("changed"));
    }

    private EmailSettingsCrypto crypto(String encryptionKey) {
        return new EmailSettingsCrypto(new RuntimeEmailProperties(
            "localhost", 1025, "", "", false, false,
            "no-reply@church.local", encryptionKey, Duration.ofMinutes(10)
        ));
    }

    private String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
