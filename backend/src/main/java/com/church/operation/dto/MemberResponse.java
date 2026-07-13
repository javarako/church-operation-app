package com.church.operation.dto;

import com.church.operation.entity.Address;
import com.church.operation.entity.Member;
import com.church.operation.util.Role;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;

public record MemberResponse(
    String id,
    String primaryEmail,
    String secondaryEmail,
    String primaryPhone,
    String secondaryPhone,
    String mobilePhone,
    Address mailingAddress,
    String displayName,
    String nickname,
    LocalDate birthDate,
    String groupCode,
    String membershipStatus,
    String offeringNumber,
    String faceImageAttachmentId,
    String householdName,
    String notes,
    Set<Role> roles,
    boolean active,
    boolean locked,
    boolean mustChangePassword,
    Instant createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getPrimaryEmail(),
            member.getSecondaryEmail(),
            member.getPrimaryPhone(),
            member.getSecondaryPhone(),
            member.getMobilePhone(),
            member.getMailingAddress(),
            member.getDisplayName(),
            member.getNickname(),
            member.getBirthDate(),
            member.getGroupCode(),
            member.getMembershipStatus(),
            member.getOfferingNumber(),
            member.getFaceImageAttachmentId(),
            member.getHouseholdName(),
            member.getNotes(),
            member.getRoles(),
            member.isActive(),
            member.isLocked(),
            member.isMustChangePassword(),
            member.getCreatedAt()
        );
    }
}
