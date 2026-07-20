package com.church.operation.service;

import com.church.operation.dto.MemberRequest;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void createsBootstrapAdminMemberWhenMissing() {
        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.empty());
        when(memberRepository.save(org.mockito.ArgumentMatchers.any(Member.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        Member member = service.createBootstrapAdminMember();

        assertThat(member.getPrimaryEmail()).isEqualTo("admin");
        assertThat(member.getRoles()).containsExactly(Role.ADMIN);
        assertThat(member.isMustChangePassword()).isTrue();
        assertThat(member.getCreatedAt()).isNotNull();
        verify(memberRepository).save(org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void rejectsBlankPrimaryEmail() {
        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        assertThatThrownBy(() -> service.validatePrimaryEmail(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Primary email is required.");
    }

    @Test
    void membershipManagerCreatesMemberWithNormalizedEmailAndDefaultMemberRole() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        MemberRequest request = new MemberRequest(
            " NEW.MEMBER@EXAMPLE.COM ",
            "secondary@example.com",
            "416-555-0123",
            null,
            null,
            null,
            "New Member",
            "Newbie",
            null,
            "YOUTH",
            "ACTIVE",
            "1001",
            null,
            "Smith",
            "Welcome note",
            null,
            null,
            null
        );

        when(memberRepository.findByPrimaryEmail("new.member@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        Member created = service.createMember(actor, request);

        assertThat(created.getPrimaryEmail()).isEqualTo("new.member@example.com");
        assertThat(created.getDisplayName()).isEqualTo("New Member");
        assertThat(created.getRoles()).containsExactly(Role.MEMBER);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void membershipManagerAssignsMultipleNormalizedCommitteeCodes() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        MemberRequest request = new MemberRequest(
            "person@example.com", null, null, null, null, null, "Person", null, null,
            "ADULT", "ACTIVE",
            new LinkedHashSet<>(List.of("worship", "WORSHIP", "outreach")),
            "1001", null, null, null, Set.of(Role.MEMBER), true, false
        );
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.COMMITTEE_CODE, "WORSHIP"))
            .thenReturn(Optional.of(activeReference("WORSHIP")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.COMMITTEE_CODE, "OUTREACH"))
            .thenReturn(Optional.of(activeReference("OUTREACH")));
        when(memberRepository.findByPrimaryEmail("person@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member created = new MemberService(memberRepository, referenceDataRepository).createMember(actor, request);

        assertThat(created.getCommitteeCodes()).containsExactly("WORSHIP", "OUTREACH");
    }

    @Test
    void rejectsUnknownCommitteeCode() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        MemberRequest request = new MemberRequest(
            "person@example.com", null, null, null, null, null, "Person", null, null,
            null, null, Set.of("MISSING"), null, null, null, null, Set.of(Role.MEMBER), true, false
        );
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.COMMITTEE_CODE, "MISSING"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            new MemberService(memberRepository, referenceDataRepository).createMember(actor, request)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Committee code was not found.");
    }

    @Test
    void membershipManagerCanRetainAnAssignedInactiveCommitteeCode() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        Member stored = member("member-id", "person@example.com", Role.MEMBER);
        stored.setCommitteeCodes(Set.of("LEGACY"));
        MemberRequest request = new MemberRequest(
            "person@example.com", null, null, null, null, null, "Person", null, null,
            null, null, Set.of("LEGACY"), null, null, null, null, Set.of(Role.MEMBER), true, false
        );
        when(memberRepository.findById("member-id")).thenReturn(Optional.of(stored));
        when(memberRepository.findByPrimaryEmail("person@example.com")).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member updated = new MemberService(memberRepository, referenceDataRepository)
            .updateMember(actor, "member-id", request);

        assertThat(updated.getCommitteeCodes()).containsExactly("LEGACY");
    }

    @Test
    void createMemberSetsCreatedAt() {
        Member actor = member("manager-id", "manager@example.com", Role.MEMBERSHIP);
        MemberRequest request = minimalRequest("new@example.com");

        when(memberRepository.findByPrimaryEmail("new@example.com")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        Member created = service.createMember(actor, request);

        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void viewerCannotCreateMember() {
        Member actor = member("viewer-id", "viewer@example.com", Role.VIEWER);
        MemberRequest request = minimalRequest("person@example.com");

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        assertThatThrownBy(() -> service.createMember(actor, request))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to manage members.");
    }

    @Test
    void rejectsOfferingNumberWithNonDigits() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        MemberRequest request = new MemberRequest(
            "person@example.com",
            null,
            null,
            null,
            null,
            null,
            "Person",
            null,
            null,
            null,
            null,
            "10A1",
            null,
            null,
            null,
            Set.of(Role.MEMBER),
            true,
            false
        );

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        assertThatThrownBy(() -> service.createMember(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Offering number must contain digits only.");
    }

    @Test
    void memberCanUpdateOwnProfileWithoutChangingRolesOrEmail() {
        Member actor = member("member-id", "member@example.com", Role.MEMBER);
        Member stored = member("member-id", "member@example.com", Role.MEMBER);
        stored.setDisplayName("Old Name");
        stored.setCommitteeCodes(Set.of("WORSHIP"));

        MemberRequest request = new MemberRequest(
            "changed@example.com",
            "secondary@example.com",
            "416-555-0199",
            null,
            null,
            null,
            "New Name",
            "Nick",
            null,
            "IGNORED",
            "IGNORED",
            Set.of("MALICIOUS"),
            "9999",
            null,
            "Household",
            "Updated self notes",
            Set.of(Role.ADMIN),
            false,
            true
        );

        when(memberRepository.findById("member-id")).thenReturn(Optional.of(stored));
        when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        Member updated = service.updateMember(actor, "member-id", request);

        assertThat(updated.getPrimaryEmail()).isEqualTo("member@example.com");
        assertThat(updated.getDisplayName()).isEqualTo("New Name");
        assertThat(updated.getGroupCode()).isNull();
        assertThat(updated.getOfferingNumber()).isNull();
        assertThat(updated.getCommitteeCodes()).containsExactly("WORSHIP");
        assertThat(updated.getRoles()).containsExactly(Role.MEMBER);
        assertThat(updated.isActive()).isTrue();
        assertThat(updated.isLocked()).isFalse();
    }

    @Test
    void adminCanUpdateOwnProfileWithoutLosingAdminRole() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        Member stored = member("admin-id", "admin", Role.ADMIN);
        stored.setDisplayName("System Administrator");
        MemberRequest request = minimalRequest("ignored@example.com");

        when(memberRepository.findById("admin-id")).thenReturn(Optional.of(stored));
        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        Member updated = service.updateSelf(actor, request);

        assertThat(updated.getPrimaryEmail()).isEqualTo("admin");
        assertThat(updated.getDisplayName()).isEqualTo("Person");
        assertThat(updated.getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void managedMemberUpdatePreservesImageStoredThroughImageEndpoint() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        Member stored = member("member-id", "member@example.com", Role.MEMBER);
        stored.setFaceImageAttachmentId("64b000000000000000000001");
        MemberRequest request = minimalRequest("member@example.com");

        when(memberRepository.findById("member-id")).thenReturn(Optional.of(stored));
        when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member updated = new MemberService(memberRepository, referenceDataRepository)
            .updateMember(actor, "member-id", request);

        assertThat(updated.getFaceImageAttachmentId()).isEqualTo("64b000000000000000000001");
    }

    @Test
    void selfProfileUpdatePreservesImageStoredThroughImageEndpoint() {
        Member actor = member("member-id", "member@example.com", Role.MEMBER);
        Member stored = member("member-id", "member@example.com", Role.MEMBER);
        stored.setFaceImageAttachmentId("64b000000000000000000001");
        MemberRequest request = minimalRequest("member@example.com");

        when(memberRepository.findById("member-id")).thenReturn(Optional.of(stored));
        when(memberRepository.findByPrimaryEmail("member@example.com")).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member updated = new MemberService(memberRepository, referenceDataRepository)
            .updateSelf(actor, request);

        assertThat(updated.getFaceImageAttachmentId()).isEqualTo("64b000000000000000000001");
    }

    @Test
    void listMembersFiltersBySearchForMembershipManager() {
        Member actor = member("manager-id", "manager@example.com", Role.MEMBERSHIP);
        Member match = member("match-id", "sarah@example.com", Role.MEMBER);
        match.setDisplayName("Sarah Kim");
        Member miss = member("miss-id", "tom@example.com", Role.MEMBER);
        miss.setDisplayName("Tom Lee");

        when(memberRepository.findAll()).thenReturn(List.of(match, miss));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        List<Member> members = service.listMembers(actor, "sarah");

        assertThat(members).containsExactly(match);
    }

    @Test
    void treasurerCanListMembersForOfferingLookup() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        Member giver = member("giver-id", "giver@example.com", Role.MEMBER);

        when(memberRepository.findAll()).thenReturn(List.of(giver));

        MemberService service = new MemberService(memberRepository, referenceDataRepository);

        List<Member> members = service.listMembers(actor, "giver");

        assertThat(members).containsExactly(giver);
    }

    private Member member(String id, String primaryEmail, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(primaryEmail);
        member.setRoles(Set.of(role));
        member.setActive(true);
        return member;
    }

    private MemberRequest minimalRequest(String primaryEmail) {
        return new MemberRequest(
            primaryEmail,
            null,
            null,
            null,
            null,
            null,
            "Person",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private ReferenceData activeReference(String code) {
        ReferenceData reference = new ReferenceData();
        reference.setType(ReferenceDataType.COMMITTEE_CODE);
        reference.setCode(code);
        reference.setActive(true);
        return reference;
    }
}
