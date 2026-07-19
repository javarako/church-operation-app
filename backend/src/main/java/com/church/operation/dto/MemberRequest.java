package com.church.operation.dto;

import com.church.operation.entity.Address;
import com.church.operation.util.Role;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Set;

public record MemberRequest(
    @NotBlank String primaryEmail,
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
    Set<String> committeeCodes,
    String offeringNumber,
    String faceImageAttachmentId,
    String householdName,
    String notes,
    Set<Role> roles,
    Boolean active,
    Boolean locked
) {
    public MemberRequest(
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
        Boolean active,
        Boolean locked
    ) {
        this(
            primaryEmail,
            secondaryEmail,
            primaryPhone,
            secondaryPhone,
            mobilePhone,
            mailingAddress,
            displayName,
            nickname,
            birthDate,
            groupCode,
            membershipStatus,
            null,
            offeringNumber,
            faceImageAttachmentId,
            householdName,
            notes,
            roles,
            active,
            locked
        );
    }
}
