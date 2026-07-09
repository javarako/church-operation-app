### Task 1: Add Budget Domain Model

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/BudgetType.java`
- Create: `backend/src/main/java/com/church/operation/entity/Budget.java`
- Create: `backend/src/main/java/com/church/operation/repo/BudgetRepository.java`

**Interfaces:**
- Produces enum values: `CARRY_OVER`, `OFFERING_INCOME`, `EXPENSE`.
- Produces entity getters/setters used by DTOs and service.
- Produces repository methods used by `BudgetService`.

- [ ] **Step 1: Create budget type enum**

```java
package com.church.operation.util;

public enum BudgetType {
    CARRY_OVER,
    OFFERING_INCOME,
    EXPENSE
}
```

- [ ] **Step 2: Create budget entity**

Create `Budget` with these fields and standard getters/setters:

```java
@Document("budgets")
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
}
```

- [ ] **Step 3: Create repository**

Create `BudgetRepository`:

```java
public interface BudgetRepository extends MongoRepository<Budget, String> {
    @Query(value = "{ 'fiscalYear' : ?0, 'deleted' : { $ne : true } }", sort = "{ 'budgetType' : 1, 'category' : 1, 'subCategory' : 1 }")
    List<Budget> findActiveByFiscalYear(int fiscalYear);

    @Query("{ 'fiscalYear' : ?0, 'budgetType' : ?1, 'category' : ?2, 'subCategory' : ?3, 'deleted' : { $ne : true } }")
    List<Budget> findActiveDuplicates(int fiscalYear, BudgetType budgetType, String category, String subCategory);

    @Query("{ 'fiscalYear' : ?0, 'budgetType' : 'CARRY_OVER', 'deleted' : { $ne : true } }")
    List<Budget> findActiveCarryOver(int fiscalYear);
}
```

- [ ] **Step 4: Compile backend**

Run: `cd backend && mvn test -DskipTests`

Expected: compile succeeds.

---

