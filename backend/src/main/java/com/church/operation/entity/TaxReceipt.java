package com.church.operation.entity;

import com.church.operation.util.TaxReceiptStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document("tax_receipts")
@CompoundIndexes({
    @CompoundIndex(name = "tax_year_offering_number", def = "{'taxYear': 1, 'offeringNumber': 1}"),
    @CompoundIndex(name = "member_tax_year", def = "{'memberId': 1, 'taxYear': 1}")
})
public class TaxReceipt {
    @Id
    private String id;
    @Indexed(unique = true)
    private String receiptNumber;
    private TaxReceiptStatus status;
    private int taxYear;
    private LocalDate issueDate;
    private String issuedByMemberId;
    private String memberId;
    private String offeringNumber;
    private String donorName;
    private String donorAddress;
    private String donorEmail;
    private String churchName;
    private String churchAddress;
    private String charityRegistrationNumber;
    private String churchWebsite;
    private String receiptIssueLocation;
    private String treasurerName;
    private BigDecimal giftAmount;
    private BigDecimal eligibleAmount;
    private BigDecimal advantageAmount;
    private String advantageDescription;
    private String thankYouNote;
    private List<String> sourceOfferingIds = new ArrayList<>();
    private String sourceChecksum;
    private String voidReason;
    private Instant voidedAt;
    private String voidedByMemberId;
    private String replacesReceiptId;
    private String replacementReceiptId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public TaxReceiptStatus getStatus() { return status; }
    public void setStatus(TaxReceiptStatus status) { this.status = status; }
    public int getTaxYear() { return taxYear; }
    public void setTaxYear(int taxYear) { this.taxYear = taxYear; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getIssuedByMemberId() { return issuedByMemberId; }
    public void setIssuedByMemberId(String issuedByMemberId) { this.issuedByMemberId = issuedByMemberId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getOfferingNumber() { return offeringNumber; }
    public void setOfferingNumber(String offeringNumber) { this.offeringNumber = offeringNumber; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getDonorAddress() { return donorAddress; }
    public void setDonorAddress(String donorAddress) { this.donorAddress = donorAddress; }
    public String getDonorEmail() { return donorEmail; }
    public void setDonorEmail(String donorEmail) { this.donorEmail = donorEmail; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public String getChurchAddress() { return churchAddress; }
    public void setChurchAddress(String churchAddress) { this.churchAddress = churchAddress; }
    public String getCharityRegistrationNumber() { return charityRegistrationNumber; }
    public void setCharityRegistrationNumber(String value) { this.charityRegistrationNumber = value; }
    public String getChurchWebsite() { return churchWebsite; }
    public void setChurchWebsite(String churchWebsite) { this.churchWebsite = churchWebsite; }
    public String getReceiptIssueLocation() { return receiptIssueLocation; }
    public void setReceiptIssueLocation(String value) { this.receiptIssueLocation = value; }
    public String getTreasurerName() { return treasurerName; }
    public void setTreasurerName(String treasurerName) { this.treasurerName = treasurerName; }
    public BigDecimal getGiftAmount() { return giftAmount; }
    public void setGiftAmount(BigDecimal giftAmount) { this.giftAmount = giftAmount; }
    public BigDecimal getEligibleAmount() { return eligibleAmount; }
    public void setEligibleAmount(BigDecimal eligibleAmount) { this.eligibleAmount = eligibleAmount; }
    public BigDecimal getAdvantageAmount() { return advantageAmount; }
    public void setAdvantageAmount(BigDecimal advantageAmount) { this.advantageAmount = advantageAmount; }
    public String getAdvantageDescription() { return advantageDescription; }
    public void setAdvantageDescription(String value) { this.advantageDescription = value; }
    public String getThankYouNote() { return thankYouNote; }
    public void setThankYouNote(String thankYouNote) { this.thankYouNote = thankYouNote; }
    public List<String> getSourceOfferingIds() { return sourceOfferingIds; }
    public void setSourceOfferingIds(List<String> sourceOfferingIds) { this.sourceOfferingIds = sourceOfferingIds; }
    public String getSourceChecksum() { return sourceChecksum; }
    public void setSourceChecksum(String sourceChecksum) { this.sourceChecksum = sourceChecksum; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidedByMemberId() { return voidedByMemberId; }
    public void setVoidedByMemberId(String value) { this.voidedByMemberId = value; }
    public String getReplacesReceiptId() { return replacesReceiptId; }
    public void setReplacesReceiptId(String replacesReceiptId) { this.replacesReceiptId = replacesReceiptId; }
    public String getReplacementReceiptId() { return replacementReceiptId; }
    public void setReplacementReceiptId(String replacementReceiptId) { this.replacementReceiptId = replacementReceiptId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
