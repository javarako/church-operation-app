package com.church.operation.entity;

import com.church.operation.util.GivingType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Document("offerings")
public class Offering {
    @Id
    private String id;

    private GivingType givingType;
    private String memberId;
    private String giverLabel;
    private String giverDisplayName;
    private LocalDate offeringDate;
    private LocalDate offeringSunday;
    private String fundCategory;
    private BigDecimal amount;
    private String paymentMethod;
    private String memo;
    private String incomeTransactionId;
    private String createdBy;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public GivingType getGivingType() { return givingType; }
    public void setGivingType(GivingType givingType) { this.givingType = givingType; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getGiverLabel() { return giverLabel; }
    public void setGiverLabel(String giverLabel) { this.giverLabel = giverLabel; }
    public String getGiverDisplayName() { return giverDisplayName; }
    public void setGiverDisplayName(String giverDisplayName) { this.giverDisplayName = giverDisplayName; }
    public LocalDate getOfferingDate() { return offeringDate; }
    public void setOfferingDate(LocalDate offeringDate) { this.offeringDate = offeringDate; }
    public LocalDate getOfferingSunday() { return offeringSunday; }
    public void setOfferingSunday(LocalDate offeringSunday) { this.offeringSunday = offeringSunday; }
    public String getFundCategory() { return fundCategory; }
    public void setFundCategory(String fundCategory) { this.fundCategory = fundCategory; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getIncomeTransactionId() { return incomeTransactionId; }
    public void setIncomeTransactionId(String incomeTransactionId) { this.incomeTransactionId = incomeTransactionId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
