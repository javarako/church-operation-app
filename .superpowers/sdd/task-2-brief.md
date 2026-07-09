### Task 2: Add DTOs And Budget Service Tests

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/BudgetRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/BudgetResponse.java`
- Create: `backend/src/test/java/com/church/operation/service/BudgetServiceTest.java`

**Interfaces:**
- Produces `BudgetRequest(int fiscalYear, BudgetType budgetType, String category, String subCategory, BigDecimal budget, String memo)`.
- Produces `BudgetResponse.from(Budget budget)`.

- [ ] **Step 1: Add DTO records**

Create `BudgetRequest`:

```java
public record BudgetRequest(
    int fiscalYear,
    BudgetType budgetType,
    String category,
    String subCategory,
    BigDecimal budget,
    String memo
) {
}
```

Create `BudgetResponse`:

```java
public record BudgetResponse(
    String id,
    int fiscalYear,
    BudgetType budgetType,
    String category,
    String subCategory,
    BigDecimal budget,
    String memo,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt
) {
    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
            budget.getId(),
            budget.getFiscalYear(),
            budget.getBudgetType(),
            budget.getCategory(),
            budget.getSubCategory(),
            budget.getBudget(),
            budget.getMemo(),
            budget.getCreatedBy(),
            budget.getCreatedAt(),
            budget.getUpdatedBy(),
            budget.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: Write service tests**

Create `BudgetServiceTest` with these tests:

```java
@Test
void treasurerCreatesCarryOverBudget() {
    Member actor = member("treasurer-id", Role.TREASURER);
    when(budgetRepository.findActiveCarryOver(2026)).thenReturn(List.of());
    when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Budget saved = service().createBudget(actor, request(2026, BudgetType.CARRY_OVER, null, null, "1500.00"));

    assertThat(saved.getBudgetType()).isEqualTo(BudgetType.CARRY_OVER);
    assertThat(saved.getCategory()).isNull();
    assertThat(saved.getSubCategory()).isNull();
    assertThat(saved.getBudget()).isEqualByComparingTo("1500.00");
}

@Test
void rejectsSecondCarryOverForFiscalYear() {
    Member actor = member("treasurer-id", Role.TREASURER);
    Budget existing = existingBudget("carry-1", BudgetType.CARRY_OVER, null, null);
    when(budgetRepository.findActiveCarryOver(2026)).thenReturn(List.of(existing));

    assertThatThrownBy(() -> service().createBudget(actor, request(2026, BudgetType.CARRY_OVER, null, null, "100.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Carry-over budget already exists for this fiscal year.");
}

@Test
void createsOfferingIncomeBudget() {
    Member actor = member("admin-id", Role.ADMIN);
    when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
        .thenReturn(Optional.of(activeReference(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE", null)));
    when(budgetRepository.findActiveDuplicates(2026, BudgetType.OFFERING_INCOME, "TITHE", null)).thenReturn(List.of());
    when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Budget saved = service().createBudget(actor, request(2026, BudgetType.OFFERING_INCOME, "TITHE", null, "50000.00"));

    assertThat(saved.getCategory()).isEqualTo("TITHE");
    assertThat(saved.getSubCategory()).isNull();
}

@Test
void createsExpenseBudgetWithFilteredSubCategory() {
    Member actor = member("treasurer-id", Role.TREASURER);
    when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
        .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE", null)));
    when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"))
        .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES", "OFFICE")));
    when(budgetRepository.findActiveDuplicates(2026, BudgetType.EXPENSE, "OFFICE", "SUPPLIES")).thenReturn(List.of());
    when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Budget saved = service().createBudget(actor, request(2026, BudgetType.EXPENSE, "OFFICE", "SUPPLIES", "2500.00"));

    assertThat(saved.getCategory()).isEqualTo("OFFICE");
    assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
}

@Test
void viewerCannotCreateBudget() {
    Member actor = member("viewer-id", Role.VIEWER);

    assertThatThrownBy(() -> service().createBudget(actor, request(2026, BudgetType.CARRY_OVER, null, null, "100.00")))
        .isInstanceOf(SecurityException.class)
        .hasMessage("You do not have permission to manage budgets.");
}

@Test
void rejectsDuplicateActiveBudget() {
    Member actor = member("admin-id", Role.ADMIN);
    Budget existing = existingBudget("budget-1", BudgetType.OFFERING_INCOME, "TITHE", null);
    when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
        .thenReturn(Optional.of(activeReference(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE", null)));
    when(budgetRepository.findActiveDuplicates(2026, BudgetType.OFFERING_INCOME, "TITHE", null)).thenReturn(List.of(existing));

    assertThatThrownBy(() -> service().createBudget(actor, request(2026, BudgetType.OFFERING_INCOME, "TITHE", null, "100.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Budget already exists for this fiscal year and category.");
}

@Test
void softDeletesBudget() {
    Member actor = member("admin-id", Role.ADMIN);
    Budget existing = existingBudget("budget-1", BudgetType.EXPENSE, "OFFICE", "SUPPLIES");
    when(budgetRepository.findById("budget-1")).thenReturn(Optional.of(existing));
    when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Budget deleted = service().deleteBudget(actor, "budget-1");

    assertThat(deleted.isDeleted()).isTrue();
    assertThat(deleted.getDeletedBy()).isEqualTo("admin-id");
    assertThat(deleted.getDeletedAt()).isNotNull();
}
```

Required helper methods in the test:

```java
private BudgetService service() {
    return new BudgetService(budgetRepository, referenceDataRepository);
}

private BudgetRequest request(int fiscalYear, BudgetType budgetType, String category, String subCategory, String budget) {
    return new BudgetRequest(fiscalYear, budgetType, category, subCategory, new BigDecimal(budget), "Annual budget");
}

private Budget existingBudget(String id, BudgetType budgetType, String category, String subCategory) {
    Budget budget = new Budget();
    budget.setId(id);
    budget.setFiscalYear(2026);
    budget.setBudgetType(budgetType);
    budget.setCategory(category);
    budget.setSubCategory(subCategory);
    budget.setBudget(new BigDecimal("100.00"));
    return budget;
}
```

- [ ] **Step 3: Run focused test to verify red**

Run: `cd backend && mvn test -Dtest=BudgetServiceTest`

Expected: FAIL because `BudgetService` does not exist.

---

