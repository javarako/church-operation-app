package com.church.operation.service;

import com.church.operation.config.PasswordResetProperties;
import com.church.operation.entity.Member;
import com.church.operation.entity.PasswordResetToken;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthTokenService authTokenService;
    @Mock private PasswordResetEmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        var properties = new PasswordResetProperties(
            "http://localhost:5173",
            Duration.ofMinutes(30),
            "no-reply@church.local"
        );
        service = new PasswordResetService(
            memberRepository,
            tokenRepository,
            passwordEncoder,
            authTokenService,
            emailService,
            properties
        );
    }

    @Test
    void requestResetStoresOnlyHashAndEmailsRawToken() {
        Member member = activeMember("member@example.com");
        when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(member));
        when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestReset(" MEMBER@example.com ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).deleteByMemberEmail("member@example.com");
        verify(tokenRepository).save(saved.capture());
        verify(emailService).sendResetEmail(eq(member), raw.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(hash(raw.getValue()));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(raw.getValue());
        assertThat(saved.getValue().getMemberEmail()).isEqualTo("member@example.com");
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(29)));
    }

    @Test
    void requestResetDoesNothingForUnknownInactiveOrLockedMember() {
        when(memberRepository.findByPrimaryEmail("unknown@example.com")).thenReturn(Optional.empty());
        Member inactive = activeMember("inactive@example.com");
        inactive.setActive(false);
        when(memberRepository.findByPrimaryEmail("inactive@example.com")).thenReturn(Optional.of(inactive));
        Member locked = activeMember("locked@example.com");
        locked.setLocked(true);
        when(memberRepository.findByPrimaryEmail("locked@example.com")).thenReturn(Optional.of(locked));

        service.requestReset("unknown@example.com");
        service.requestReset("inactive@example.com");
        service.requestReset("locked@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendResetEmail(any(), any());
    }

    @Test
    void successfulResetUpdatesPasswordConsumesTokenAndRevokesSessions() {
        String rawToken = "raw-reset-token";
        PasswordResetToken token = validToken(rawToken, "member@example.com");
        Member member = activeMember("member@example.com");
        member.setMustChangePassword(true);
        when(tokenRepository.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(token));
        when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword(rawToken, "new-password");

        assertThat(member.getPasswordHash()).isEqualTo("new-hash");
        assertThat(member.isMustChangePassword()).isFalse();
        assertThat(token.getUsedAt()).isNotNull();
        verify(memberRepository).save(member);
        verify(tokenRepository).save(token);
        verify(authTokenService).revokeAllForMember("member@example.com");
    }

    @Test
    void resetRejectsUnknownExpiredOrUsedTokens() {
        when(tokenRepository.findByTokenHash(hash("unknown"))).thenReturn(Optional.empty());
        PasswordResetToken expired = validToken("expired", "member@example.com");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(hash("expired"))).thenReturn(Optional.of(expired));
        PasswordResetToken used = validToken("used", "member@example.com");
        used.setUsedAt(Instant.now());
        when(tokenRepository.findByTokenHash(hash("used"))).thenReturn(Optional.of(used));

        assertInvalidToken("unknown");
        assertInvalidToken("expired");
        assertInvalidToken("used");
    }

    @Test
    void resetRejectsShortPassword() {
        assertThatThrownBy(() -> service.resetPassword("token", "short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("New password must be at least 8 characters.");
    }

    private void assertInvalidToken(String rawToken) {
        assertThatThrownBy(() -> service.resetPassword(rawToken, "new-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("This password reset link is invalid or expired.");
    }

    private Member activeMember(String email) {
        Member member = new Member();
        member.setPrimaryEmail(email);
        member.setDisplayName("Member");
        member.setActive(true);
        return member;
    }

    private PasswordResetToken validToken(String rawToken, String email) {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setMemberEmail(email);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
        return token;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
