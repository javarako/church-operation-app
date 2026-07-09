### Task 3: Implement Budget Service

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/BudgetService.java`
- Modify: `backend/src/test/java/com/church/operation/service/BudgetServiceTest.java`

**Interfaces:**
- Produces `List<Budget> listBudgets(Member actor, int fiscalYear)`.
- Produces `Budget createBudget(Member actor, BudgetRequest request)`.
- Produces `Budget updateBudget(Member actor, String id, BudgetRequest request)`.
- Produces `Budget deleteBudget(Member actor, String id)`.

- [ ] **Step 1: Add service constructor and public methods**

Create `BudgetService` with dependencies:

```java
private final BudgetRepository budgetRepository;
private final ReferenceDataRepository referenceDataRepository;
```

- [ ] **Step 2: Implement role checks**

Rules:

- `ADMIN` and `TREASURER` can list/create/update/delete budgets.
- all other roles throw `SecurityException("You do not have permission to manage budgets.")`.

- [ ] **Step 3: Implement request validation**

Use exact validation messages:

- `"Fiscal year is required."`
- `"Fiscal year must be between 2000 and 2100."`
- `"Budget type is required."`
- `"Budget must be zero or greater."`
- `"Budget category is required."`
- `"Offering fund/category was not found."`
- `"Financial category was not found."`
- `"Financial sub-category was not found for the selected category."`
- `"Carry-over budget already exists for this fiscal year."`
- `"Budget already exists for this fiscal year and category."`
- `"Budget was not found."`

Normalize category and sub-category by trimming and uppercasing with `Locale.ROOT`.

- [ ] **Step 4: Implement budget-type validation**

Carry-over:

```java
budget.setCategory(null);
budget.setSubCategory(null);
ensureUniqueCarryOver(request.fiscalYear(), budget.getId());
```

Offering income:

```java
referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, normalizedCategory)
    .filter(ReferenceData::isActive)
    .orElseThrow(() -> new IllegalArgumentException("Offering fund/category was not found."));
budget.setCategory(normalizedCategory);
budget.setSubCategory(null);
ensureUniqueBudget(request.fiscalYear(), BudgetType.OFFERING_INCOME, normalizedCategory, null, budget.getId());
```

Expense:

```java
referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, normalizedCategory)
    .filter(ReferenceData::isActive)
    .orElseThrow(() -> new IllegalArgumentException("Financial category was not found."));
String normalizedSubCategory = normalizeSubCategory(normalizedCategory, request.subCategory());
budget.setCategory(normalizedCategory);
budget.setSubCategory(normalizedSubCategory);
ensureUniqueBudget(request.fiscalYear(), BudgetType.EXPENSE, normalizedCategory, normalizedSubCategory, budget.getId());
```

- [ ] **Step 5: Implement create/update/delete timestamps**

Create:

```java
budget.setCreatedBy(actor.getId());
budget.setCreatedAt(Instant.now());
```

Update:

```java
budget.setUpdatedBy(actor.getId());
budget.setUpdatedAt(Instant.now());
```

Delete:

```java
budget.setDeleted(true);
budget.setDeletedBy(actor.getId());
budget.setDeletedAt(Instant.now());
```

- [ ] **Step 6: Run focused tests**

Run: `cd backend && mvn test -Dtest=BudgetServiceTest`

Expected: PASS.

---

