package com.church.operation.service;

import com.church.operation.dto.MemberRequest;
import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member createBootstrapAdminMember() {
        Optional<Member> existing = memberRepository.findByPrimaryEmail("admin");
        if (existing.isPresent()) {
            return existing.get();
        }

        Member member = new Member();
        member.setPrimaryEmail("admin");
        member.setDisplayName("System Administrator");
        member.setRoles(Set.of(Role.ADMIN));
        member.setActive(true);
        member.setLocked(false);
        member.setMustChangePassword(true);
        return memberRepository.save(member);
    }

    public Optional<Member> findByPrimaryEmail(String primaryEmail) {
        validatePrimaryEmail(primaryEmail);
        return memberRepository.findByPrimaryEmail(normalize(primaryEmail));
    }

    public Member save(Member member) {
        validatePrimaryEmail(member.getPrimaryEmail());
        member.setPrimaryEmail(normalize(member.getPrimaryEmail()));
        return memberRepository.save(member);
    }

    public List<Member> listMembers(Member actor, String search) {
        requireMembershipManager(actor);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return memberRepository.findAll().stream()
            .filter(member -> matchesSearch(member, normalizedSearch))
            .toList();
    }

    public Member getMember(Member actor, String id) {
        Member member = findRequired(id);
        if (isMembershipManager(actor) || Objects.equals(actor.getId(), member.getId())) {
            return member;
        }
        throw new SecurityException("You do not have permission to view this member.");
    }

    public Member createMember(Member actor, MemberRequest request) {
        requireMembershipManager(actor);
        Member member = new Member();
        applyManagedFields(member, request);
        ensureUniqueIdentity(member);
        return memberRepository.save(member);
    }

    public Member updateMember(Member actor, String id, MemberRequest request) {
        Member member = findRequired(id);
        if (isMembershipManager(actor)) {
            applyManagedFields(member, request);
        } else if (Objects.equals(actor.getId(), member.getId()) && hasRole(actor, Role.MEMBER)) {
            applySelfServiceFields(member, request);
        } else {
            throw new SecurityException("You do not have permission to update this member.");
        }
        ensureUniqueIdentity(member);
        return memberRepository.save(member);
    }

    public Member getSelf(Member actor) {
        return findRequired(actor.getId());
    }

    public Member updateSelf(Member actor, MemberRequest request) {
        Member member = findRequired(actor.getId());
        applySelfServiceFields(member, request);
        ensureUniqueIdentity(member);
        return memberRepository.save(member);
    }

    void validatePrimaryEmail(String primaryEmail) {
        if (primaryEmail == null || primaryEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary email is required.");
        }
    }

    private Member findRequired(String id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Member was not found."));
    }

    private void applyManagedFields(Member member, MemberRequest request) {
        validatePrimaryEmail(request.primaryEmail());
        member.setPrimaryEmail(normalize(request.primaryEmail()));
        member.setSecondaryEmail(trimToNull(request.secondaryEmail()));
        member.setPrimaryPhone(trimToNull(request.primaryPhone()));
        member.setSecondaryPhone(trimToNull(request.secondaryPhone()));
        member.setMobilePhone(trimToNull(request.mobilePhone()));
        member.setMailingAddress(request.mailingAddress());
        member.setDisplayName(trimToNull(request.displayName()));
        member.setNickname(trimToNull(request.nickname()));
        member.setBirthDate(request.birthDate());
        member.setGroupCode(trimToNull(request.groupCode()));
        member.setMembershipStatus(trimToNull(request.membershipStatus()));
        member.setOfferingNumber(trimToNull(request.offeringNumber()));
        member.setFaceImageAttachmentId(trimToNull(request.faceImageAttachmentId()));
        member.setHouseholdName(trimToNull(request.householdName()));
        member.setNotes(trimToNull(request.notes()));
        member.setRoles(normalizeRoles(request.roles()));
        member.setActive(request.active() == null || request.active());
        member.setLocked(request.locked() != null && request.locked());
    }

    private void applySelfServiceFields(Member member, MemberRequest request) {
        member.setSecondaryEmail(trimToNull(request.secondaryEmail()));
        member.setPrimaryPhone(trimToNull(request.primaryPhone()));
        member.setSecondaryPhone(trimToNull(request.secondaryPhone()));
        member.setMobilePhone(trimToNull(request.mobilePhone()));
        member.setMailingAddress(request.mailingAddress());
        member.setDisplayName(trimToNull(request.displayName()));
        member.setNickname(trimToNull(request.nickname()));
        member.setBirthDate(request.birthDate());
        member.setFaceImageAttachmentId(trimToNull(request.faceImageAttachmentId()));
        member.setHouseholdName(trimToNull(request.householdName()));
        member.setNotes(trimToNull(request.notes()));
    }

    private void ensureUniqueIdentity(Member member) {
        memberRepository.findByPrimaryEmail(member.getPrimaryEmail())
            .filter(existing -> !Objects.equals(existing.getId(), member.getId()))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Primary email is already used by another member.");
            });

        if (member.getOfferingNumber() != null) {
            if (!member.getOfferingNumber().matches("\\d+")) {
                throw new IllegalArgumentException("Offering number must contain digits only.");
            }
            memberRepository.findByOfferingNumber(member.getOfferingNumber())
                .filter(existing -> !Objects.equals(existing.getId(), member.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Offering number is already used by another member.");
                });
        }
    }

    private boolean matchesSearch(Member member, String search) {
        if (search.isEmpty()) {
            return true;
        }
        return contains(member.getPrimaryEmail(), search)
            || contains(member.getDisplayName(), search)
            || contains(member.getNickname(), search)
            || contains(member.getOfferingNumber(), search)
            || contains(member.getGroupCode(), search);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private Set<Role> normalizeRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return new LinkedHashSet<>(Set.of(Role.MEMBER));
        }
        return new LinkedHashSet<>(roles);
    }

    private void requireMembershipManager(Member actor) {
        if (!isMembershipManager(actor)) {
            throw new SecurityException("You do not have permission to manage members.");
        }
    }

    private boolean isMembershipManager(Member member) {
        return hasRole(member, Role.ADMIN) || hasRole(member, Role.MEMBERSHIP);
    }

    private boolean hasRole(Member member, Role role) {
        return member != null && member.getRoles() != null && member.getRoles().contains(role);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
