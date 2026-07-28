package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.dto.EmailSettingsResetRequest;
import com.church.operation.dto.EmailSettingsResponse;
import com.church.operation.dto.EmailSettingsSaveRequest;
import com.church.operation.dto.EmailSettingsTestRequest;
import com.church.operation.dto.EmailSettingsTestResponse;
import com.church.operation.entity.EmailSettings;
import com.church.operation.entity.Member;
import com.church.operation.repo.EmailSettingsRepository;
import com.church.operation.util.EmailSettingsSource;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Service
public class EmailSettingsService {
    private static final String TEST_SUBJECT = "Church Operations email settings test";
    private static final String TEST_BODY =
        "This message confirms that the Church Operations email settings can deliver email successfully.";

    private final EmailSettingsRepository repository;
    private final EmailSettingsResolver resolver;
    private final EmailSettingsCrypto crypto;
    private final RuntimeEmailSender sender;
    private final EmailSettingsVerificationStore verificationStore;
    private final RuntimeEmailProperties properties;
    private final Clock clock;
    private final SystemAuditService audit;

    @Autowired
    public EmailSettingsService(
        EmailSettingsRepository repository,
        EmailSettingsResolver resolver,
        EmailSettingsCrypto crypto,
        RuntimeEmailSender sender,
        EmailSettingsVerificationStore verificationStore,
        RuntimeEmailProperties properties,
        SystemAuditService audit
    ) {
        this(repository, resolver, crypto, sender, verificationStore, properties, Clock.systemUTC(), audit);
    }

    EmailSettingsService(
        EmailSettingsRepository repository,
        EmailSettingsResolver resolver,
        EmailSettingsCrypto crypto,
        RuntimeEmailSender sender,
        EmailSettingsVerificationStore verificationStore,
        RuntimeEmailProperties properties,
        Clock clock
    ) {
        this(repository, resolver, crypto, sender, verificationStore, properties, clock, null);
    }

    private EmailSettingsService(
        EmailSettingsRepository repository,
        EmailSettingsResolver resolver,
        EmailSettingsCrypto crypto,
        RuntimeEmailSender sender,
        EmailSettingsVerificationStore verificationStore,
        RuntimeEmailProperties properties,
        Clock clock,
        SystemAuditService audit
    ) {
        this.repository = repository;
        this.resolver = resolver;
        this.crypto = crypto;
        this.sender = sender;
        this.verificationStore = verificationStore;
        this.properties = properties;
        this.clock = clock;
        this.audit = audit;
    }

    public EmailSettingsResponse get(Member actor) {
        requireAdmin(actor);
        Optional<EmailSettings> saved = repository.findById(EmailSettings.SINGLETON_ID);
        return saved.map(this::response).orElseGet(this::serverDefaultResponse);
    }

    public EmailSettingsTestResponse test(Member actor, EmailSettingsTestRequest request) {
        try {
            EmailSettingsTestResponse response = testOperation(actor, request);
            recordSuccess(actor, SystemAuditOperation.EMAIL_SETTINGS_TEST, EmailSettingsSource.DATABASE, 1);
            return response;
        } catch (RuntimeException exception) {
            recordFailure(actor, SystemAuditOperation.EMAIL_SETTINGS_TEST, EmailSettingsSource.DATABASE, 1, exception);
            throw exception;
        }
    }

    private EmailSettingsTestResponse testOperation(Member actor, EmailSettingsTestRequest request) {
        requireAdmin(actor);
        EmailSettingsDraft draft = draft(
            request.host(), request.port(), request.username(), request.password(), request.fromAddress()
        );
        EffectiveEmailSettings effective = resolver.resolveDraft(draft);
        byte[] fingerprint = null;
        try {
            validate(effective, request.testRecipient());
            fingerprint = fingerprint(effective);
            sender.send(effective, normalizeEmail(request.testRecipient()), TEST_SUBJECT, TEST_BODY);
            var verification = verificationStore.remember(actor.getId(), fingerprint);
            return new EmailSettingsTestResponse(
                verification.token(), verification.expiresAt(), "Test email sent successfully."
            );
        } finally {
            if (fingerprint != null) {
                Arrays.fill(fingerprint, (byte) 0);
            }
            effective.clearPassword();
        }
    }

    public EmailSettingsResponse save(Member actor, EmailSettingsSaveRequest request) {
        try {
            EmailSettingsResponse response = saveOperation(actor, request);
            recordSuccess(actor, SystemAuditOperation.EMAIL_SETTINGS_UPDATE, response.source(), 1);
            return response;
        } catch (RuntimeException exception) {
            recordFailure(actor, SystemAuditOperation.EMAIL_SETTINGS_UPDATE, EmailSettingsSource.DATABASE, 1, exception);
            throw exception;
        }
    }

    private EmailSettingsResponse saveOperation(Member actor, EmailSettingsSaveRequest request) {
        requireAdmin(actor);
        EmailSettingsDraft draft = draft(
            request.host(), request.port(), request.username(), request.password(), request.fromAddress()
        );
        EffectiveEmailSettings effective = resolver.resolveDraft(draft);
        byte[] fingerprint = null;
        try {
            validate(effective, null);
            fingerprint = fingerprint(effective);
            verificationStore.consume(request.verificationToken(), actor.getId(), fingerprint);

            EmailSettings saved = repository.findById(EmailSettings.SINGLETON_ID).orElseGet(EmailSettings::new);
            Instant now = clock.instant();
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(now);
                saved.setCreatedByMemberId(actor.getId());
            }
            saved.setHost(effective.host());
            saved.setPort(effective.port());
            saved.setUsername(effective.username());
            saved.setFromAddress(effective.fromAddress());
            String suppliedPassword = request.password() == null ? "" : request.password();
            if (!suppliedPassword.isBlank()) {
                var encrypted = crypto.encrypt(suppliedPassword.toCharArray());
                saved.setPasswordCiphertext(encrypted.ciphertext());
                saved.setPasswordNonce(encrypted.nonce());
                saved.setCipherVersion(encrypted.version());
            }
            saved.setUpdatedAt(now);
            saved.setUpdatedByMemberId(actor.getId());
            return response(repository.save(saved));
        } finally {
            if (fingerprint != null) {
                Arrays.fill(fingerprint, (byte) 0);
            }
            effective.clearPassword();
        }
    }

    public EmailSettingsResponse reset(Member actor, EmailSettingsResetRequest request) {
        try {
            EmailSettingsResponse response = resetOperation(actor, request);
            recordSuccess(actor, SystemAuditOperation.EMAIL_SETTINGS_RESET, response.source(), 0);
            return response;
        } catch (RuntimeException exception) {
            recordFailure(
                actor, SystemAuditOperation.EMAIL_SETTINGS_RESET,
                EmailSettingsSource.SERVER_DEFAULTS, 0, exception
            );
            throw exception;
        }
    }

    private EmailSettingsResponse resetOperation(Member actor, EmailSettingsResetRequest request) {
        requireAdmin(actor);
        EffectiveEmailSettings defaults = resolver.serverDefaults();
        try {
            validate(defaults, request.testRecipient());
            sender.send(defaults, normalizeEmail(request.testRecipient()), TEST_SUBJECT, TEST_BODY);
            repository.deleteById(EmailSettings.SINGLETON_ID);
            return serverDefaultResponse();
        } finally {
            defaults.clearPassword();
        }
    }

    private EmailSettingsDraft draft(String host, int port, String username, String password, String fromAddress) {
        return new EmailSettingsDraft(
            trim(host), port, trim(username), password == null ? new char[0] : password.toCharArray(),
            normalizeEmail(fromAddress)
        );
    }

    private void validate(EffectiveEmailSettings settings, String recipient) {
        if (settings.host().isBlank() || settings.host().length() > 253) {
            throw new IllegalArgumentException("SMTP host is required and must be 253 characters or fewer.");
        }
        if (settings.port() < 1 || settings.port() > 65535) {
            throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
        }
        if (settings.auth() && settings.username().isBlank()) {
            throw new IllegalArgumentException("SMTP username is required when authentication is enabled.");
        }
        char[] password = settings.password();
        try {
            if (settings.auth() && password.length == 0) {
                throw new IllegalArgumentException("SMTP password is required when authentication is enabled.");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        validateEmail(settings.fromAddress(), "From address");
        if (recipient != null) {
            validateEmail(recipient, "Test recipient");
        }
    }

    private void validateEmail(String value, String label) {
        try {
            InternetAddress address = new InternetAddress(normalizeEmail(value), true);
            address.validate();
        } catch (AddressException exception) {
            throw new IllegalArgumentException(label + " must be a valid email address.");
        }
    }

    private byte[] fingerprint(EffectiveEmailSettings settings) {
        char[] password = settings.password();
        try {
            StringBuilder canonical = new StringBuilder();
            append(canonical, settings.host().toLowerCase(Locale.ROOT));
            append(canonical, Integer.toString(settings.port()));
            append(canonical, settings.username());
            append(canonical, new String(password));
            append(canonical, settings.fromAddress().toLowerCase(Locale.ROOT));
            return crypto.fingerprint(canonical.toString());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private EmailSettingsResponse response(EmailSettings saved) {
        return new EmailSettingsResponse(
            saved.getHost(), saved.getPort(), saved.getUsername(), saved.getFromAddress(),
            saved.hasEncryptedPassword() || hasText(properties.password()),
            EmailSettingsSource.DATABASE, saved.getUpdatedAt()
        );
    }

    private EmailSettingsResponse serverDefaultResponse() {
        return new EmailSettingsResponse(
            trim(properties.host()), properties.port(), trim(properties.username()),
            normalizeEmail(properties.fromAddress()), hasText(properties.password()),
            EmailSettingsSource.SERVER_DEFAULTS, null
        );
    }

    private void requireAdmin(Member actor) {
        if (actor == null || actor.getRoles() == null || !actor.getRoles().contains(Role.ADMIN)) {
            throw new SecurityException("Administrator access is required.");
        }
    }

    private String normalizeEmail(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void recordSuccess(
        Member actor,
        SystemAuditOperation operation,
        EmailSettingsSource source,
        int version
    ) {
        if (audit != null) {
            audit.recordSuccess(actor, operation, java.util.Map.of(
                "configurationSource", source.name(),
                "configurationVersion", version
            ));
        }
    }

    private void recordFailure(
        Member actor,
        SystemAuditOperation operation,
        EmailSettingsSource source,
        int version,
        RuntimeException failure
    ) {
        if (audit != null) {
            audit.recordFailure(actor, operation, java.util.Map.of(
                "configurationSource", source.name(),
                "configurationVersion", version
            ), failure);
        }
    }
}
