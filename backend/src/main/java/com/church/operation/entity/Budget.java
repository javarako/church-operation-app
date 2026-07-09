package com.church.operation.entity;

import com.church.operation.util.BudgetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("budgets")
@CompoundIndexes({
    @CompoundIndex(
        name = "active_carry_over_budget_unique",
        def = "{'fiscalYear': 1, 'budgetType': 1}",
        unique = true,
        partialFilter = "{'deleted': false, 'budgetType': 'CARRY_OVER'}"
    ),
    @CompoundIndex(
        name = "active_category_budget_unique",
        def = "{'fiscalYear': 1, 'budgetType': 1, 'category': 1, 'subCategory': 1}",
        unique = true,
        partialFilter = "{'deleted': false, 'budgetType': {'$in': ['OFFERING_INCOME', 'EXPENSE']}}"
    )
})
public class Budget {
    @Id
    private String id;

    private int fiscalYear;
    private BudgetType budgetType;
    private String category;
    private String subCategory;
    private BigDecimal budget;
    private String memo;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private boolean deleted = false;
    private String deletedBy;
    private Instant deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public BudgetType getBudgetType() { return budgetType; }
    public void setBudgetType(BudgetType budgetType) { this.budgetType = budgetType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
