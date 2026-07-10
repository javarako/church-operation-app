package com.church.operation.service;

import com.church.operation.dto.LoginRequest;
import com.church.operation.dto.LoginResponse;
import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenService authTokenService;

    @Test
    void loginReturnsForcedPasswordChangeForBootstrapAdmin() {
        Member admin = new Member();
        admin.setPrimaryEmail("admin");
        admin.setDisplayName("System Administrator");
        admin.setPasswordHash("hashed-password");
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setMustChangePassword(true);
        admin.setActive(true);

        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(authTokenService.issueToken(admin)).thenReturn("issued-token");

        AuthService service = new AuthService(memberRepository, passwordEncoder, authTokenService);

        LoginResponse response = service.login(new LoginRequest("admin", "password"));

        assertThat(response.primaryEmail()).isEqualTo("admin");
        assertThat(response.roles()).containsExactly(Role.ADMIN);
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.token()).isEqualTo("issued-token");
    }

    @Test
    void loginRejectsInvalidPassword() {
        Member admin = new Member();
        admin.setPrimaryEmail("admin");
        admin.setPasswordHash("hashed-password");
        admin.setActive(true);

        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("bad", "hashed-password")).thenReturn(false);

        AuthService service = new AuthService(memberRepository, passwordEncoder, authTokenService);

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "bad")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid username or password.");
    }

    @Test
    void logoutRevokesThePresentedToken() {
        AuthService service = new AuthService(memberRepository, passwordEncoder, authTokenService);

        service.logout("issued-token");

        verify(authTokenService).revokeToken("issued-token");
    }
}
