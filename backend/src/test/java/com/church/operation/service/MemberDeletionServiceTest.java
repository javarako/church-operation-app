package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.PasswordResetTokenRepository;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberDeletionServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private MemberImageService memberImageService;
    @Mock private MongoTemplate mongoTemplate;

    @Test
    void deletesAnUnreferencedMemberAndRelatedCredentials() {
        Member actor = member("manager-id", "manager@example.com", Role.MEMBERSHIP);
        Member target = member("member-id", "member@example.com", Role.MEMBER);
        when(memberRepository.findById("member-id")).thenReturn(Optional.of(target));
        when(mongoTemplate.exists(any(Query.class), eq(Offering.class))).thenReturn(false);
        when(mongoTemplate.exists(any(Query.class), eq(TaxReceipt.class))).thenReturn(false);

        service().delete(actor, "member-id");

        verify(memberImageService).remove(actor, "member-id");
        verify(tokenRepository).deleteByMemberEmail("member@example.com");
        verify(memberRepository).delete(target);
    }

    @Test
    void blocksDeletingTheSignedInMember() {
        Member actor = member("member-id", "manager@example.com", Role.ADMIN);
        when(memberRepository.findById("member-id")).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service().delete(actor, "member-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("signed-in member");
    }

    @Test
    void blocksDeletingTheBootstrapAdministrator() {
        Member actor = member("manager-id", "manager@example.com", Role.ADMIN);
        Member target = member("admin-id", "admin", Role.ADMIN);
        when(memberRepository.findById("admin-id")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().delete(actor, "admin-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("System Administrator");
    }

    @Test
    void blocksDeletingAMemberReferencedByAnOffering() {
        Member actor = member("manager-id", "manager@example.com", Role.MEMBERSHIP);
        Member target = member("member-id", "member@example.com", Role.MEMBER);
        when(memberRepository.findById("member-id")).thenReturn(Optional.of(target));
        when(mongoTemplate.exists(any(Query.class), eq(Offering.class))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(actor, "member-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("offering");
    }

    private MemberDeletionService service() {
        return new MemberDeletionService(memberRepository, tokenRepository, memberImageService, mongoTemplate);
    }

    private Member member(String id, String email, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(email);
        member.setRoles(Set.of(role));
        return member;
    }
}
