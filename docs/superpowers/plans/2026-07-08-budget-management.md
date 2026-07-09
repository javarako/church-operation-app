# Budget Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `/budgets` so Admin and Treasurer users can maintain fiscal-year carry-over, offering income, and expense budget rows.

**Architecture:** Add a MongoDB `Budget` document with soft-delete metadata, a `BudgetService` for validation and role checks, and a `BudgetController` for CRUD APIs. The Vue screen follows the existing Members/Offerings/Finance list-detail pattern and reuses reference-data dropdowns.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Vue 3.x, Vue Router, MongoDB 6+, JUnit 5, Mockito, Spring Test, Vitest/Jest, Vue Testing Library.

## Global Constraints

- Java package structure remains under `com.church.operation` using `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`.
- Budget field must be named `budget`, not estimated amount.
- Budget types are `CARRY_OVER`, `OFFERING_INCOME`, and `EXPENSE`.
- One active carry-over row is allowed per fiscal year.
- Offering income budgets use active `OFFERING_FUND_CATEGORY` reference data.
- Expense budgets use active `FINANCIAL_CATEGORY` and optional active `FINANCIAL_SUB_CATEGORY` filtered by selected category.
- Budget UI must use the same list/detail edit pattern as Members, Offerings, and Finance.
- Delete uses a trash-bin icon and soft-deletes the budget.
- Backend verification command: `cd backend && mvn test`.
- Frontend verification command: `cd frontend && npm run build`.

---

## File Structure

- Create `backend/src/main/java/com/church/operation/util/BudgetType.java`: budget type enum.
- Create `backend/src/main/java/com/church/operation/entity/Budget.java`: Mongo budget document.
- Create `backend/src/main/java/com/church/operation/repo/BudgetRepository.java`: budget persistence queries.
- Create `backend/src/main/java/com/church/operation/dto/BudgetRequest.java`: create/update request payload.
- Create `backend/src/main/java/com/church/operation/dto/BudgetResponse.java`: API response mapper.
- Create `backend/src/main/java/com/church/operation/service/BudgetService.java`: role checks, validation, duplicate detection, soft delete.
- Create `backend/src/main/java/com/church/operation/rest/BudgetController.java`: `/api/budgets` endpoints.
- Create `backend/src/test/java/com/church/operation/service/BudgetServiceTest.java`: service behavior tests.
- Create `frontend/src/api/budgets.ts`: budget API client and types.
- Create `frontend/src/views/BudgetsView.vue`: budget management UI.
- Modify `frontend/src/router/index.ts`: route `/budgets` to `BudgetsView`.

---

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

### Task 4: Add Budget REST API

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/BudgetController.java`

**Interfaces:**
- Produces `GET /api/budgets?fiscalYear=2026`.
- Produces `POST /api/budgets`.
- Produces `PUT /api/budgets/{id}`.
- Produces `DELETE /api/budgets/{id}`.

- [ ] **Step 1: Add controller**

Create `BudgetController`:

```java
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    List<BudgetResponse> listBudgets(Authentication authentication, @RequestParam("fiscalYear") int fiscalYear) {
        return budgetService.listBudgets(actor(authentication), fiscalYear).stream()
            .map(BudgetResponse::from)
            .toList();
    }

    @PostMapping
    BudgetResponse createBudget(Authentication authentication, @RequestBody BudgetRequest request) {
        return BudgetResponse.from(budgetService.createBudget(actor(authentication), request));
    }

    @PutMapping("/{id}")
    BudgetResponse updateBudget(Authentication authentication, @PathVariable("id") String id, @RequestBody BudgetRequest request) {
        return BudgetResponse.from(budgetService.updateBudget(actor(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    BudgetResponse deleteBudget(Authentication authentication, @PathVariable("id") String id) {
        return BudgetResponse.from(budgetService.deleteBudget(actor(authentication), id));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
```

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

---

### Task 5: Add Frontend Budget API And Route

**Files:**
- Create: `frontend/src/api/budgets.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Produces `listBudgets(fiscalYear: number): Promise<Budget[]>`.
- Produces `createBudget(payload: BudgetPayload): Promise<Budget>`.
- Produces `updateBudget(id: string, payload: BudgetPayload): Promise<Budget>`.
- Produces `deleteBudget(id: string): Promise<Budget>`.

- [ ] **Step 1: Add API client**

Create `frontend/src/api/budgets.ts`:

```ts
import { deleteJson, getJson, postJson, putJson } from './http';

export type BudgetType = 'CARRY_OVER' | 'OFFERING_INCOME' | 'EXPENSE';

export interface Budget {
  id: string;
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export interface BudgetPayload {
  fiscalYear: number;
  budgetType: BudgetType;
  category?: string;
  subCategory?: string;
  budget: number;
  memo?: string;
}

export function listBudgets(fiscalYear: number) {
  return getJson<Budget[]>(`/api/budgets?fiscalYear=${encodeURIComponent(String(fiscalYear))}`);
}

export function createBudget(payload: BudgetPayload) {
  return postJson<BudgetPayload, Budget>('/api/budgets', payload);
}

export function updateBudget(id: string, payload: BudgetPayload) {
  return putJson<BudgetPayload, Budget>(`/api/budgets/${id}`, payload);
}

export function deleteBudget(id: string) {
  return deleteJson<Budget>(`/api/budgets/${id}`);
}
```

- [ ] **Step 2: Route to real view**

Import `BudgetsView` and replace the `/budgets` placeholder with:

```ts
{ path: '/budgets', component: BudgetsView, meta: { roles: financeRoles } },
```

- [ ] **Step 3: Run frontend build to verify missing view**

Run: `cd frontend && npm run build`

Expected: FAIL because `BudgetsView.vue` does not exist.

---

### Task 6: Build Budget Management UI

**Files:**
- Create: `frontend/src/views/BudgetsView.vue`
- Uses: `frontend/src/api/budgets.ts`, `frontend/src/api/referenceData.ts`

**Interfaces:**
- Consumes active offering funds, financial categories, and filtered financial sub-categories.
- Consumes budget CRUD API.

- [ ] **Step 1: Create view using existing layout classes**

Use `workspace`, `page-header`, `two-column`, `panel`, `toolbar`, `table-wrap`, `form-grid`, `actions`, `row-actions`, and `icon-button`.

Header:

```html
<h2>Budgets</h2>
<button type="button" @click="startCreate">Add budget</button>
```

- [ ] **Step 2: Add toolbar and table**

Toolbar controls:

```html
<input v-model.number="selectedFiscalYear" type="number" min="2000" max="2100" @change="loadBudgets" />
<select v-model="filters.budgetType">
  <option value="">All types</option>
  <option value="CARRY_OVER">Carry over</option>
  <option value="OFFERING_INCOME">Offering income</option>
  <option value="EXPENSE">Expense</option>
</select>
<button type="button" @click="loadBudgets">Refresh</button>
```

Table columns:

```text
Type, Fiscal year, Category, Sub-category, Budget, Memo, [trash icon]
```

Row behavior:

- clicking any row calls `selectBudget(budget)`
- selected row gets `selected` class
- trash icon calls `deleteSelectedBudget(budget)`

- [ ] **Step 3: Add budget form**

Fields:

- budget type dropdown
- fiscal year
- budget
- category dropdown
- sub-category dropdown for expense only
- memo

Form title:

```html
<h3>{{ editingBudgetId ? 'Budget Detail' : 'New Budget' }}</h3>
```

- [ ] **Step 4: Implement category/sub-category behavior**

Rules:

- `CARRY_OVER`: hide category and sub-category controls.
- `OFFERING_INCOME`: load category options from `OFFERING_FUND_CATEGORY`.
- `EXPENSE`: load category options from `FINANCIAL_CATEGORY`.
- Expense sub-categories load from `FINANCIAL_SUB_CATEGORY` with selected category as parent.

Functions:

```ts
async function handleBudgetTypeChange() {
  form.category = '';
  form.subCategory = '';
  await loadCategoryOptions();
  await loadSubCategoryOptions();
}

async function handleCategoryChange() {
  form.subCategory = '';
  await loadSubCategoryOptions();
}
```

- [ ] **Step 5: Implement save/delete**

Save:

- require numeric `budget`
- create when no selected id
- update when selected id exists
- reload budget list
- reset form
- show `Saved` or `Updated`

Delete:

- confirm with `window.confirm`
- call `deleteBudget(id)`
- reload list
- reset form if selected
- show `Deleted`

- [ ] **Step 6: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

---

### Task 7: Full Verification And Commit

**Files:**
- Source files from Tasks 1-6.

**Interfaces:**
- Verifies backend, frontend, and local startup when Docker is available.

- [ ] **Step 1: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

- [ ] **Step 3: Rebuild local Docker app**

Run: `docker compose up -d --build`

Expected: Mongo, backend, and frontend containers are running. If the approval gate blocks Docker, report the blocker and continue with source/test verification status.

- [ ] **Step 4: Check local frontend and backend**

Run: `curl -I http://localhost:5173`

Expected: `HTTP/1.1 200 OK`.

Run: `curl -s http://localhost:8080/actuator/health`

Expected: status `UP`.

- [ ] **Step 5: Stage source files only**

Stage:

```bash
git add backend/src/main/java/com/church/operation/util/BudgetType.java \
  backend/src/main/java/com/church/operation/entity/Budget.java \
  backend/src/main/java/com/church/operation/repo/BudgetRepository.java \
  backend/src/main/java/com/church/operation/dto/BudgetRequest.java \
  backend/src/main/java/com/church/operation/dto/BudgetResponse.java \
  backend/src/main/java/com/church/operation/service/BudgetService.java \
  backend/src/main/java/com/church/operation/rest/BudgetController.java \
  backend/src/test/java/com/church/operation/service/BudgetServiceTest.java \
  frontend/src/api/budgets.ts \
  frontend/src/views/BudgetsView.vue \
  frontend/src/router/index.ts
```

Do not stage generated `backend/target` or `frontend/dist` files.

- [ ] **Step 6: Commit implementation**

Run:

```bash
git commit -m "Add budget management"
```

Expected: commit succeeds.

---

## Self-Review

- Spec coverage: carry-over, offering income, expense budgets, filtered sub-categories, duplicate protection, same UI pattern, trash delete, and verification are covered.
- Placeholder scan: plan contains no placeholder markers, unfinished stubs, or undefined method references.
- Type consistency: `BudgetType`, `BudgetRequest`, `BudgetResponse`, `BudgetService`, `/api/budgets`, and frontend budget API names are consistent across tasks.
