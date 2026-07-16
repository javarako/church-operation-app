package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthTokenServiceTest {
    private final AuthTokenService service = new AuthTokenService(mock(MemberRepository.class));

    @Test
    void revokedTokenCannotResolveMember() {
        String token = service.issueToken(activeMember("member@example.com"));

        service.revokeToken(token);

        assertThat(service.findMember(token)).isEmpty();
    }

    @Test
    void revokingMemberRemovesEveryIssuedToken() {
        Member member = activeMember("member@example.com");
        String first = service.issueToken(member);
        String second = service.issueToken(member);

        service.revokeAllForMember("MEMBER@example.com");

        assertThat(service.findMember(first)).isEmpty();
        assertThat(service.findMember(second)).isEmpty();
    }

    @Test
    void revokingAllRemovesTokensForEveryMemberAfterDatabaseRestore() {
        String first = service.issueToken(activeMember("first@example.com"));
        String second = service.issueToken(activeMember("second@example.com"));

        service.revokeAll();

        assertThat(service.findMember(first)).isEmpty();
        assertThat(service.findMember(second)).isEmpty();
    }

    private Member activeMember(String primaryEmail) {
        Member member = new Member();
        member.setPrimaryEmail(primaryEmail);
        member.setActive(true);
        return member;
    }
}
