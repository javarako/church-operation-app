package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.entity.EmailSettings;
import com.church.operation.repo.EmailSettingsRepository;
import com.church.operation.util.EmailSettingsSource;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmailSettingsResolver {
    private final EmailSettingsRepository repository;
    private final EmailSettingsCrypto crypto;
    private final RuntimeEmailProperties properties;

    public EmailSettingsResolver(
        EmailSettingsRepository repository,
        EmailSettingsCrypto crypto,
        RuntimeEmailProperties properties
    ) {
        this.repository = repository;
        this.crypto = crypto;
        this.properties = properties;
    }

    public EffectiveEmailSettings resolve() {
        return repository.findById(EmailSettings.SINGLETON_ID)
            .map(this::databaseSettings)
            .orElseGet(this::serverDefaults);
    }

    public EffectiveEmailSettings resolveDraft(EmailSettingsDraft draft) {
        char[] supplied = draft.password();
        char[] effectivePassword = supplied.length == 0 ? activePassword() : supplied;
        return new EffectiveEmailSettings(
            trim(draft.host()),
            draft.port(),
            trim(draft.username()),
            effectivePassword,
            properties.auth(),
            properties.startTls(),
            trim(draft.fromAddress()),
            EmailSettingsSource.DATABASE
        );
    }

    public EffectiveEmailSettings serverDefaults() {
        return new EffectiveEmailSettings(
            trim(properties.host()),
            properties.port(),
            trim(properties.username()),
            chars(properties.password()),
            properties.auth(),
            properties.startTls(),
            trim(properties.fromAddress()),
            EmailSettingsSource.SERVER_DEFAULTS
        );
    }

    public Optional<EmailSettings> savedSettings() {
        return repository.findById(EmailSettings.SINGLETON_ID);
    }

    private EffectiveEmailSettings databaseSettings(EmailSettings saved) {
        return new EffectiveEmailSettings(
            trim(saved.getHost()),
            saved.getPort(),
            trim(saved.getUsername()),
            password(saved),
            properties.auth(),
            properties.startTls(),
            trim(saved.getFromAddress()),
            EmailSettingsSource.DATABASE
        );
    }

    private char[] activePassword() {
        return repository.findById(EmailSettings.SINGLETON_ID)
            .map(this::password)
            .orElseGet(() -> chars(properties.password()));
    }

    private char[] password(EmailSettings saved) {
        if (!saved.hasEncryptedPassword()) {
            return chars(properties.password());
        }
        return crypto.decrypt(new EmailSettingsCrypto.EncryptedSecret(
            saved.getPasswordCiphertext(), saved.getPasswordNonce(), saved.getCipherVersion()
        ));
    }

    private char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
