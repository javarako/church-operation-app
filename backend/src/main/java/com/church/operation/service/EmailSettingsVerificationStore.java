package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailSettingsVerificationStore {
    private static final String RETEST_MESSAGE = "Test the email settings again before saving.";

    private final RuntimeEmailProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Verification> verifications = new ConcurrentHashMap<>();

    @Autowired
    public EmailSettingsVerificationStore(RuntimeEmailProperties properties) {
        this(properties, Clock.systemUTC());
    }

    EmailSettingsVerificationStore(RuntimeEmailProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public VerificationToken remember(String actorId, byte[] fingerprint) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Arrays.fill(tokenBytes, (byte) 0);
        Instant expiresAt = clock.instant().plus(properties.verificationLifetime());
        verifications.put(token, new Verification(actorId, Arrays.copyOf(fingerprint, fingerprint.length), expiresAt));
        return new VerificationToken(token, expiresAt);
    }

    public void consume(String token, String actorId, byte[] fingerprint) {
        Verification verification = token == null ? null : verifications.remove(token);
        if (verification == null || !clock.instant().isBefore(verification.expiresAt())) {
            throw new IllegalArgumentException(RETEST_MESSAGE);
        }
        if (!verification.actorId().equals(actorId)) {
            throw new SecurityException("The email-settings test belongs to another administrator.");
        }
        if (!MessageDigest.isEqual(verification.fingerprint(), fingerprint)) {
            throw new IllegalArgumentException(RETEST_MESSAGE);
        }
    }

    public record VerificationToken(String token, Instant expiresAt) {
    }

    private record Verification(String actorId, byte[] fingerprint, Instant expiresAt) {
    }
}
