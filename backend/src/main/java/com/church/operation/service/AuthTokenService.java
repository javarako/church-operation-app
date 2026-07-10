package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthTokenService {
    private final MemberRepository memberRepository;
    private final Map<String, String> tokenToPrimaryEmail = new ConcurrentHashMap<>();

    public AuthTokenService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public String issueToken(Member member) {
        String token = UUID.randomUUID().toString();
        tokenToPrimaryEmail.put(token, member.getPrimaryEmail());
        return token;
    }

    public Optional<Member> findMember(String token) {
        String primaryEmail = tokenToPrimaryEmail.get(token);
        if (primaryEmail == null) {
            return Optional.empty();
        }
        return memberRepository.findByPrimaryEmail(primaryEmail)
            .filter(Member::isActive)
            .filter(member -> !member.isLocked());
    }

    public void revokeToken(String token) {
        tokenToPrimaryEmail.remove(token);
    }

    public void revokeAllForMember(String primaryEmail) {
        String normalized = primaryEmail.trim().toLowerCase();
        tokenToPrimaryEmail.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(normalized));
    }
}
