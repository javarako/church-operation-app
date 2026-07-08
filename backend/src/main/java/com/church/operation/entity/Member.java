package com.church.operation.entity;

import com.church.operation.util.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Document("members")
public class Member {
    @Id
    private String id;

    @Indexed(unique = true)
    private String primaryEmail;

    private String secondaryEmail;
    private String primaryPhone;
    private String secondaryPhone;
    private String mobilePhone;
    private Address mailingAddress;
    private String displayName;
    private String nickname;
    private LocalDate birthDate;
    private String groupCode;
    private String membershipStatus;

    @Indexed(unique = true, sparse = true)
    private String offeringNumber;

    private String faceImageAttachmentId;
    private String householdName;
    private String notes;
    private Set<Role> roles = new LinkedHashSet<>();
    private boolean active = true;
    private boolean locked = false;
    private boolean mustChangePassword = false;
    private String passwordHash;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }
    public String getSecondaryEmail() { return secondaryEmail; }
    public void setSecondaryEmail(String secondaryEmail) { this.secondaryEmail = secondaryEmail; }
    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }
    public String getSecondaryPhone() { return secondaryPhone; }
    public void setSecondaryPhone(String secondaryPhone) { this.secondaryPhone = secondaryPhone; }
    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
    public Address getMailingAddress() { return mailingAddress; }
    public void setMailingAddress(Address mailingAddress) { this.mailingAddress = mailingAddress; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(String membershipStatus) { this.membershipStatus = membershipStatus; }
    public String getOfferingNumber() { return offeringNumber; }
    public void setOfferingNumber(String offeringNumber) { this.offeringNumber = offeringNumber; }
    public String getFaceImageAttachmentId() { return faceImageAttachmentId; }
    public void setFaceImageAttachmentId(String faceImageAttachmentId) { this.faceImageAttachmentId = faceImageAttachmentId; }
    public String getHouseholdName() { return householdName; }
    public void setHouseholdName(String householdName) { this.householdName = householdName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
