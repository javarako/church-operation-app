package com.church.operation.entity;

import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Document("financialTransactions")
public class FinancialTransaction {
    @Id
    private String id;

    private FinancialTransactionType type;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String category;
    private String subCategory;
    private FinancialSourceType sourceType;
    private String sourceId;
    private String memo;
    private boolean hstIncluded = false;
    private String chequeNo;
    private boolean chequeCleared = false;
    private String payableTo;
    private String treasurer;
    private String createdBy;
    private Instant createdAt;
    private boolean deleted = false;
    private String deletedBy;
    private Instant deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public FinancialTransactionType getType() { return type; }
    public void setType(FinancialTransactionType type) { this.type = type; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public FinancialSourceType getSourceType() { return sourceType; }
    public void setSourceType(FinancialSourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public boolean isHstIncluded() { return hstIncluded; }
    public void setHstIncluded(boolean hstIncluded) { this.hstIncluded = hstIncluded; }
    public String getChequeNo() { return chequeNo; }
    public void setChequeNo(String chequeNo) { this.chequeNo = chequeNo; }
    public boolean isChequeCleared() { return chequeCleared; }
    public void setChequeCleared(boolean chequeCleared) { this.chequeCleared = chequeCleared; }
    public String getPayableTo() { return payableTo; }
    public void setPayableTo(String payableTo) { this.payableTo = payableTo; }
    public String getTreasurer() { return treasurer; }
    public void setTreasurer(String treasurer) { this.treasurer = treasurer; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
