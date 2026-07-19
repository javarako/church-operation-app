package com.church.operation.entity;

import com.church.operation.util.FiscalArchiveStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Document("fiscalArchiveRegistries")
public class FiscalArchiveRegistry {
    @Id
    private String id;
    @Indexed(unique = true)
    private String archiveId;
    @Indexed
    private String checksum;
    @Indexed
    private int fiscalYear;
    private LocalDate startDate;
    private LocalDate endDate;
    @Indexed
    private FiscalArchiveStatus status;
    private long offeringCount;
    private long linkedIncomeCount;
    private long expenseCount;
    private long budgetCount;
    private Set<String> memberIds = new HashSet<>();
    private Set<String> groupCodes = new HashSet<>();
    private Set<String> membershipStatuses = new HashSet<>();
    private Set<String> offeringFunds = new HashSet<>();
    private Set<String> offeringCategories = new HashSet<>();
    private Set<String> fundCategories = new HashSet<>();
    private Set<String> paymentMethods = new HashSet<>();
    private Set<String> categories = new HashSet<>();
    private Set<String> subCategories = new HashSet<>();
    private String actorId;
    private Instant createdAt;
    private Instant cleanedAt;
    private Instant restoredAt;
    private boolean downloaded;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArchiveId() { return archiveId; }
    public void setArchiveId(String archiveId) { this.archiveId = archiveId; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public FiscalArchiveStatus getStatus() { return status; }
    public void setStatus(FiscalArchiveStatus status) { this.status = status; }
    public long getOfferingCount() { return offeringCount; }
    public void setOfferingCount(long offeringCount) { this.offeringCount = offeringCount; }
    public long getLinkedIncomeCount() { return linkedIncomeCount; }
    public void setLinkedIncomeCount(long linkedIncomeCount) { this.linkedIncomeCount = linkedIncomeCount; }
    public long getExpenseCount() { return expenseCount; }
    public void setExpenseCount(long expenseCount) { this.expenseCount = expenseCount; }
    public long getBudgetCount() { return budgetCount; }
    public void setBudgetCount(long budgetCount) { this.budgetCount = budgetCount; }
    public Set<String> getMemberIds() { return memberIds; }
    public void setMemberIds(Set<String> memberIds) { this.memberIds = memberIds; }
    public Set<String> getGroupCodes() { return groupCodes; }
    public void setGroupCodes(Set<String> groupCodes) { this.groupCodes = groupCodes; }
    public Set<String> getMembershipStatuses() { return membershipStatuses; }
    public void setMembershipStatuses(Set<String> membershipStatuses) { this.membershipStatuses = membershipStatuses; }
    public Set<String> getOfferingFunds() { return offeringFunds; }
    public void setOfferingFunds(Set<String> offeringFunds) { this.offeringFunds = offeringFunds; }
    public Set<String> getOfferingCategories() { return offeringCategories; }
    public void setOfferingCategories(Set<String> offeringCategories) { this.offeringCategories = offeringCategories; }
    public Set<String> getFundCategories() { return fundCategories; }
    public void setFundCategories(Set<String> fundCategories) { this.fundCategories = fundCategories; }
    public Set<String> getPaymentMethods() { return paymentMethods; }
    public void setPaymentMethods(Set<String> paymentMethods) { this.paymentMethods = paymentMethods; }
    public Set<String> getCategories() { return categories; }
    public void setCategories(Set<String> categories) { this.categories = categories; }
    public Set<String> getSubCategories() { return subCategories; }
    public void setSubCategories(Set<String> subCategories) { this.subCategories = subCategories; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCleanedAt() { return cleanedAt; }
    public void setCleanedAt(Instant cleanedAt) { this.cleanedAt = cleanedAt; }
    public Instant getRestoredAt() { return restoredAt; }
    public void setRestoredAt(Instant restoredAt) { this.restoredAt = restoredAt; }
    public boolean isDownloaded() { return downloaded; }
    public void setDownloaded(boolean downloaded) { this.downloaded = downloaded; }
}
