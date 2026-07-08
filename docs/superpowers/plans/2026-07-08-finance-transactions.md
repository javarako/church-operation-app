# Finance Transactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `/finance` screen and backend APIs so Admin/Treasurer users can view offering income and add/edit/delete manual expense transactions.

**Architecture:** Reuse the existing MongoDB `FinancialTransaction` document created by Offering Management. Extend it with expense-only fields and soft-delete metadata. Add a `FinancialTransactionService` and `FinanceController`; the Vue view follows the Members/Offerings list-detail pattern.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Vue 3.x, Vue Router, MongoDB 6+, JUnit 5, Mockito, Spring Test, Vitest/Jest, Vue Testing Library.

## Global Constraints

- Java package structure remains under `com.church.operation` using `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`.
- Finance UI must use the same list/detail edit pattern as Members and Offerings: row click loads editable detail, selected row is highlighted, and there is no text Edit button.
- Expense delete uses a trash-bin icon and soft-deletes/cancels the transaction.
- Offering-generated income rows are read-only in Finance.
- Expense category must match active `FINANCIAL_CATEGORY` reference data.
- Expense sub-category, when present, must match active `FINANCIAL_SUB_CATEGORY` with `parentCode` equal to the selected category.
- Backend verification command: `cd backend && mvn test`.
- Frontend verification command: `cd frontend && npm run build`.

---

## File Structure

- Modify `backend/src/main/java/com/church/operation/entity/FinancialTransaction.java`: add expense fields and soft-delete metadata.
- Modify `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`: add non-deleted sorted list query.
- Create `backend/src/main/java/com/church/operation/dto/FinancialTransactionRequest.java`: manual expense payload.
- Create `backend/src/main/java/com/church/operation/dto/FinancialTransactionResponse.java`: finance API response.
- Create `backend/src/main/java/com/church/operation/service/FinancialTransactionService.java`: list/create/update/delete manual expenses.
- Create `backend/src/main/java/com/church/operation/rest/FinanceController.java`: `/api/finance/transactions` and `/api/finance/expenses`.
- Create `backend/src/test/java/com/church/operation/service/FinancialTransactionServiceTest.java`: service behavior.
- Create `frontend/src/api/finance.ts`: finance API client.
- Create `frontend/src/views/FinanceView.vue`: finance list/detail UI.
- Modify `frontend/src/router/index.ts`: route `/finance` to `FinanceView`.

---

### Task 1: Extend Finance Transaction Model

**Files:**
- Modify: `backend/src/main/java/com/church/operation/entity/FinancialTransaction.java`
- Modify: `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`

**Interfaces:**
- Produces fields: `hstIncluded`, `chequeNo`, `chequeCleared`, `payableTo`, `treasurer`, `deleted`, `deletedBy`, `deletedAt`.
- Produces repository method: `List<FinancialTransaction> findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc()`.

- [ ] **Step 1: Add model fields**

Add these private fields to `FinancialTransaction`:

```java
private boolean hstIncluded = false;
private String chequeNo;
private boolean chequeCleared = false;
private String payableTo;
private String treasurer;
private boolean deleted = false;
private String deletedBy;
private Instant deletedAt;
```

Add standard getters/setters:

```java
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
public boolean isDeleted() { return deleted; }
public void setDeleted(boolean deleted) { this.deleted = deleted; }
public String getDeletedBy() { return deletedBy; }
public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
public Instant getDeletedAt() { return deletedAt; }
public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
```

- [ ] **Step 2: Add repository query**

Modify `FinancialTransactionRepository`:

```java
public interface FinancialTransactionRepository extends MongoRepository<FinancialTransaction, String> {
    List<FinancialTransaction> findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc();
}
```

- [ ] **Step 3: Compile backend**

Run: `cd backend && mvn test -DskipTests`

Expected: compile succeeds.

---

### Task 2: Add Finance DTOs And Service Tests

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/FinancialTransactionRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/FinancialTransactionResponse.java`
- Create: `backend/src/test/java/com/church/operation/service/FinancialTransactionServiceTest.java`

**Interfaces:**
- Produces `FinancialTransactionRequest(LocalDate transactionDate, BigDecimal amount, String category, String subCategory, boolean hstIncluded, String chequeNo, boolean chequeCleared, String payableTo, String treasurer, String memo)`.
- Produces `FinancialTransactionResponse.from(FinancialTransaction transaction)`.

- [ ] **Step 1: Add DTO records**

Create `FinancialTransactionRequest`:

```java
public record FinancialTransactionRequest(
    LocalDate transactionDate,
    BigDecimal amount,
    String category,
    String subCategory,
    boolean hstIncluded,
    String chequeNo,
    boolean chequeCleared,
    String payableTo,
    String treasurer,
    String memo
) {}
```

Create `FinancialTransactionResponse` with all fields from the entity, including `sourceType`, `sourceId`, and expense fields.

- [ ] **Step 2: Write failing service tests**

Create `FinancialTransactionServiceTest` with these test methods:

```java
@Test
void treasurerCreatesManualExpense() {
    Member treasurer = memberWithRole(Role.TREASURER);
    FinancialTransactionRequest request = expenseRequest("OFFICE", "SUPPLIES");
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
        .willReturn(Optional.of(activeReference("OFFICE", null)));
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"))
        .willReturn(Optional.of(activeReference("SUPPLIES", "OFFICE")));
    given(financialTransactionRepository.save(any(FinancialTransaction.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FinancialTransaction saved = service.createExpense(treasurer, request);

    assertThat(saved.getType()).isEqualTo(FinancialTransactionType.EXPENSE);
    assertThat(saved.getSourceType()).isEqualTo(FinancialSourceType.MANUAL);
    assertThat(saved.getCategory()).isEqualTo("OFFICE");
    assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
    assertThat(saved.getAmount()).isEqualByComparingTo("25.50");
}

@Test
void viewerCanListTransactionsReadOnly() {
    Member viewer = memberWithRole(Role.VIEWER);
    FinancialTransaction income = incomeTransaction();
    given(financialTransactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc())
        .willReturn(List.of(income));

    List<FinancialTransaction> transactions = service.listTransactions(viewer);

    assertThat(transactions).containsExactly(income);
}

@Test
void rejectsExpenseSubCategoryOutsideSelectedCategory() {
    Member treasurer = memberWithRole(Role.TREASURER);
    FinancialTransactionRequest request = expenseRequest("OFFICE", "UTILITIES");
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
        .willReturn(Optional.of(activeReference("OFFICE", null)));
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "UTILITIES"))
        .willReturn(Optional.of(activeReference("UTILITIES", "BUILDING")));

    assertThatThrownBy(() -> service.createExpense(treasurer, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Financial sub-category was not found for the selected category.");
}

@Test
void updatesManualExpense() {
    Member treasurer = memberWithRole(Role.TREASURER);
    FinancialTransaction existing = manualExpense("expense-1");
    FinancialTransactionRequest request = expenseRequest("OFFICE", "SUPPLIES");
    given(financialTransactionRepository.findById("expense-1")).willReturn(Optional.of(existing));
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
        .willReturn(Optional.of(activeReference("OFFICE", null)));
    given(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"))
        .willReturn(Optional.of(activeReference("SUPPLIES", "OFFICE")));
    given(financialTransactionRepository.save(any(FinancialTransaction.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FinancialTransaction saved = service.updateExpense(treasurer, "expense-1", request);

    assertThat(saved.getCategory()).isEqualTo("OFFICE");
    assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
    assertThat(saved.getSourceType()).isEqualTo(FinancialSourceType.MANUAL);
}

@Test
void softDeletesManualExpense() {
    Member treasurer = memberWithRole(Role.TREASURER);
    FinancialTransaction existing = manualExpense("expense-1");
    given(financialTransactionRepository.findById("expense-1")).willReturn(Optional.of(existing));
    given(financialTransactionRepository.save(any(FinancialTransaction.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FinancialTransaction deleted = service.deleteExpense(treasurer, "expense-1");

    assertThat(deleted.isDeleted()).isTrue();
    assertThat(deleted.getDeletedBy()).isEqualTo(treasurer.getId());
    assertThat(deleted.getDeletedAt()).isNotNull();
}

@Test
void rejectsUpdateForOfferingGeneratedIncome() {
    Member treasurer = memberWithRole(Role.TREASURER);
    FinancialTransaction income = incomeTransaction();
    given(financialTransactionRepository.findById(income.getId())).willReturn(Optional.of(income));

    assertThatThrownBy(() -> service.updateExpense(treasurer, income.getId(), expenseRequest("OFFICE", "SUPPLIES")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Offering-generated income cannot be edited from Finance.");
}
```

Required assertions:

```java
assertThat(saved.getType()).isEqualTo(FinancialTransactionType.EXPENSE);
assertThat(saved.getSourceType()).isEqualTo(FinancialSourceType.MANUAL);
assertThat(saved.getCategory()).isEqualTo("OFFICE");
assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
assertThat(saved.isDeleted()).isTrue();
```

Use exact validation messages:

- `"You do not have permission to manage finance transactions."`
- `"Transaction date is required."`
- `"Expense amount must be greater than zero."`
- `"Financial category is required."`
- `"Financial category was not found."`
- `"Financial sub-category was not found for the selected category."`
- `"Expense transaction was not found."`
- `"Offering-generated income cannot be edited from Finance."`
- `"Offering-generated income cannot be deleted from Finance."`

- [ ] **Step 3: Run focused test to verify red**

Run: `cd backend && mvn test -Dtest=FinancialTransactionServiceTest`

Expected: FAIL because `FinancialTransactionService` does not exist.

---

### Task 3: Implement Finance Service

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/FinancialTransactionService.java`
- Modify: `backend/src/test/java/com/church/operation/service/FinancialTransactionServiceTest.java`

**Interfaces:**
- Produces `List<FinancialTransaction> listTransactions(Member actor)`.
- Produces `FinancialTransaction createExpense(Member actor, FinancialTransactionRequest request)`.
- Produces `FinancialTransaction updateExpense(Member actor, String id, FinancialTransactionRequest request)`.
- Produces `FinancialTransaction deleteExpense(Member actor, String id)`.

- [ ] **Step 1: Add service skeleton**

Create `FinancialTransactionService` with dependencies:

```java
private final FinancialTransactionRepository financialTransactionRepository;
private final ReferenceDataRepository referenceDataRepository;
```

Constructor accepts both.

- [ ] **Step 2: Implement role checks**

Rules:

- `ADMIN` and `TREASURER` can list/create/update/delete.
- `VIEWER` can list only.
- all others throw `SecurityException("You do not have permission to manage finance transactions.")`.

- [ ] **Step 3: Implement validation helpers**

Category validation:

```java
referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, normalizedCategory)
    .filter(ReferenceData::isActive)
    .orElseThrow(() -> new IllegalArgumentException("Financial category was not found."));
```

Sub-category validation when provided:

```java
referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, normalizedSubCategory)
    .filter(ReferenceData::isActive)
    .filter(referenceData -> normalizedCategory.equals(referenceData.getParentCode()))
    .orElseThrow(() -> new IllegalArgumentException("Financial sub-category was not found for the selected category."));
```

- [ ] **Step 4: Implement create/update/delete**

Create expenses with:

```java
transaction.setType(FinancialTransactionType.EXPENSE);
transaction.setSourceType(FinancialSourceType.MANUAL);
transaction.setCreatedBy(actor.getId());
transaction.setCreatedAt(Instant.now());
```

Update/delete must load active manual expense rows only. If row has `sourceType = OFFERING`, throw the offering-generated messages above.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn test -Dtest=FinancialTransactionServiceTest`

Expected: PASS.

---

### Task 4: Add Finance REST API

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/FinanceController.java`

**Interfaces:**
- Produces `GET /api/finance/transactions`.
- Produces `POST /api/finance/expenses`.
- Produces `PUT /api/finance/expenses/{id}`.
- Produces `DELETE /api/finance/expenses/{id}`.

- [ ] **Step 1: Add controller**

Create `FinanceController`:

```java
@RestController
@RequestMapping("/api/finance")
public class FinanceController {
    private final FinancialTransactionService financialTransactionService;

    public FinanceController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @GetMapping("/transactions")
    List<FinancialTransactionResponse> listTransactions(Authentication authentication) {
        return financialTransactionService.listTransactions(actor(authentication)).stream()
            .map(FinancialTransactionResponse::from)
            .toList();
    }

    @PostMapping("/expenses")
    FinancialTransactionResponse createExpense(Authentication authentication, @RequestBody FinancialTransactionRequest request) {
        return FinancialTransactionResponse.from(financialTransactionService.createExpense(actor(authentication), request));
    }

    @PutMapping("/expenses/{id}")
    FinancialTransactionResponse updateExpense(Authentication authentication, @PathVariable("id") String id, @RequestBody FinancialTransactionRequest request) {
        return FinancialTransactionResponse.from(financialTransactionService.updateExpense(actor(authentication), id, request));
    }

    @DeleteMapping("/expenses/{id}")
    FinancialTransactionResponse deleteExpense(Authentication authentication, @PathVariable("id") String id) {
        return FinancialTransactionResponse.from(financialTransactionService.deleteExpense(actor(authentication), id));
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

### Task 5: Add Frontend Finance API And Route

**Files:**
- Create: `frontend/src/api/finance.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Produces `listFinanceTransactions(): Promise<FinancialTransaction[]>`.
- Produces `createExpense(payload: FinancialTransactionPayload): Promise<FinancialTransaction>`.
- Produces `updateExpense(id: string, payload: FinancialTransactionPayload): Promise<FinancialTransaction>`.
- Produces `deleteExpense(id: string): Promise<FinancialTransaction>`.

- [ ] **Step 1: Add API client**

Create `frontend/src/api/finance.ts`:

```ts
import { deleteJson, getJson, postJson, putJson } from './http';

export type FinancialTransactionType = 'INCOME' | 'EXPENSE';
export type FinancialSourceType = 'OFFERING' | 'MANUAL';

export interface FinancialTransaction {
  id: string;
  type: FinancialTransactionType;
  transactionDate: string;
  amount: number;
  category: string;
  subCategory?: string;
  hstIncluded: boolean;
  chequeNo?: string;
  chequeCleared: boolean;
  payableTo?: string;
  treasurer?: string;
  memo?: string;
  sourceType: FinancialSourceType;
  sourceId?: string;
}

export interface FinancialTransactionPayload {
  transactionDate: string;
  amount: number;
  category: string;
  subCategory?: string;
  hstIncluded: boolean;
  chequeNo?: string;
  chequeCleared: boolean;
  payableTo?: string;
  treasurer?: string;
  memo?: string;
}

export function listFinanceTransactions() {
  return getJson<FinancialTransaction[]>('/api/finance/transactions');
}

export function createExpense(payload: FinancialTransactionPayload) {
  return postJson<FinancialTransactionPayload, FinancialTransaction>('/api/finance/expenses', payload);
}

export function updateExpense(id: string, payload: FinancialTransactionPayload) {
  return putJson<FinancialTransactionPayload, FinancialTransaction>(`/api/finance/expenses/${id}`, payload);
}

export function deleteExpense(id: string) {
  return deleteJson<FinancialTransaction>(`/api/finance/expenses/${id}`);
}
```

- [ ] **Step 2: Route to real view**

Import `FinanceView` and replace the `/finance` placeholder with:

```ts
{ path: '/finance', component: FinanceView, meta: { roles: financeRoles } },
```

- [ ] **Step 3: Run frontend build to verify missing view**

Run: `cd frontend && npm run build`

Expected: FAIL because `FinanceView.vue` does not exist yet.

---

### Task 6: Build Finance Transactions UI

**Files:**
- Create: `frontend/src/views/FinanceView.vue`
- Uses: `frontend/src/api/finance.ts`, `frontend/src/api/referenceData.ts`, `frontend/src/auth/authStore.ts`

**Interfaces:**
- Consumes active finance categories and filtered sub-categories.
- Consumes finance transaction list and expense mutation APIs.

- [ ] **Step 1: Create view using existing layout classes**

Use `workspace`, `page-header`, `two-column`, `panel`, `toolbar`, `table-wrap`, `form-grid`, `actions`, `row-actions`, and `icon-button`.

Header:

```html
<h2>Finance</h2>
<button type="button" @click="startCreate">Add expense</button>
```

- [ ] **Step 2: Add transaction table**

Columns:

```text
Type, Date, Category, Amount, HST, Cheque #, Cleared, Payable To, Approved By, Source, [trash icon]
```

Row behavior:

- If `transaction.type === 'EXPENSE' && transaction.sourceType === 'MANUAL'`, clicking row calls `selectExpense(transaction)`.
- Selected manual expense row gets `selected` class.
- Income rows do not enter edit mode.
- Trash icon appears only for manual expenses and calls `deleteSelectedExpense(transaction)`.

- [ ] **Step 3: Add expense form**

Fields:

- transaction date
- amount
- category dropdown
- sub-category dropdown filtered by selected category
- HST included checkbox
- cheque number
- cheque cleared checkbox
- payable to
- treasurer
- memo

Form title:

```html
<h3>{{ selectedExpenseId ? 'Expense Detail' : 'New Expense' }}</h3>
```

- [ ] **Step 4: Implement category/sub-category loading**

Load categories:

```ts
financialCategoryOptions.value = await listReferenceData('FINANCIAL_CATEGORY');
```

On category change:

```ts
financialSubCategoryOptions.value = form.category
  ? await listReferenceData('FINANCIAL_SUB_CATEGORY', form.category)
  : [];
form.subCategory = '';
```

- [ ] **Step 5: Implement save/delete**

Save:

- create when no selected id
- update when selected id exists
- reload transaction list
- reset form
- show `Saved`

Delete:

- confirm with `window.confirm`
- call `deleteExpense(id)`
- reload list
- reset form if selected
- show `Deleted`

- [ ] **Step 6: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

---

### Task 7: Full Verification And Local Docker Refresh

**Files:**
- No source edits expected.

**Interfaces:**
- Verifies backend, frontend, and local app startup.

- [ ] **Step 1: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

- [ ] **Step 3: Rebuild local Docker app**

Run: `docker compose up -d --build`

Expected: Mongo, backend, and frontend containers are running.

- [ ] **Step 4: Check local frontend and backend**

Run: `curl -I http://localhost:5173`

Expected: `HTTP/1.1 200 OK`.

Run: `curl -s http://localhost:8080/actuator/health`

Expected: status `UP`.

---

## Self-Review

- Spec coverage: finance list, offering income read-only display, manual expense create/edit/delete, filtered sub-categories, same UI pattern, trash-bin delete, and verification are covered.
- Placeholder scan: plan contains no placeholder markers, unfinished stubs, or undefined method references.
- Type consistency: `FinancialTransactionRequest`, `FinancialTransactionResponse`, `FinancialTransactionService`, `/api/finance/*`, and frontend finance API names are consistent across tasks.
