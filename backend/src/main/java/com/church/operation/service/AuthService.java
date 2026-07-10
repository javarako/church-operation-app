package com.church.operation.service;

import com.church.operation.dto.ChangePasswordRequest;
import com.church.operation.dto.LoginRequest;
import com.church.operation.dto.LoginResponse;
import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, AuthTokenService authTokenService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByPrimaryEmail(normalize(request.username()))
            .filter(Member::isActive)
            .filter(candidate -> !candidate.isLocked())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        return new LoginResponse(
            member.getPrimaryEmail(),
            member.getDisplayName(),
            member.getRoles(),
            member.isMustChangePassword(),
            authTokenService.issueToken(member)
        );
    }

    public void changePassword(ChangePasswordRequest request) {
        Member member = memberRepository.findByPrimaryEmail(normalize(request.username()))
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        if (request.newPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        member.setMustChangePassword(false);
        memberRepository.save(member);
    }

    public void logout(String token) {
        authTokenService.revokeToken(token);
    }

    private String normalize(String username) {
        return username.trim().toLowerCase();
    }
}
