package com.church.operation.service;

import com.church.operation.config.PasswordResetProperties;
import com.church.operation.entity.Member;
import com.church.operation.entity.PasswordResetToken;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String INVALID_TOKEN_MESSAGE = "This password reset link is invalid or expired.";

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final PasswordResetEmailService emailService;
    private final PasswordResetProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
        MemberRepository memberRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordEncoder passwordEncoder,
        AuthTokenService authTokenService,
        PasswordResetEmailService emailService,
        PasswordResetProperties properties
    ) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.properties = properties;
    }

    public void requestReset(String email) {
        String normalizedEmail = normalize(email);
        memberRepository.findByPrimaryEmail(normalizedEmail)
            .filter(Member::isActive)
            .filter(member -> !member.isLocked())
            .ifPresent(this::createAndSendToken);
    }

    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
            .filter(candidate -> candidate.getUsedAt() == null)
            .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new IllegalArgumentException(INVALID_TOKEN_MESSAGE));

        Member member = memberRepository.findByPrimaryEmail(token.getMemberEmail())
            .filter(Member::isActive)
            .filter(candidate -> !candidate.isLocked())
            .orElseThrow(() -> new IllegalArgumentException(INVALID_TOKEN_MESSAGE));

        member.setPasswordHash(passwordEncoder.encode(newPassword));
        member.setMustChangePassword(false);
        token.setUsedAt(Instant.now());
        memberRepository.save(member);
        tokenRepository.save(token);
        authTokenService.revokeAllForMember(member.getPrimaryEmail());
    }

    private void createAndSendToken(Member member) {
        String email = normalize(member.getPrimaryEmail());
        tokenRepository.deleteByMemberEmail(email);

        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setMemberEmail(email);
        token.setExpiresAt(Instant.now().plus(properties.tokenLifetime()));
        tokenRepository.save(token);

        try {
            emailService.sendResetEmail(member, rawToken);
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to send password reset email.", ex);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
