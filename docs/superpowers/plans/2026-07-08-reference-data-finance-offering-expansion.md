# Reference Data Finance And Offering Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend reference data to manage offering fund/category, financial category, and financial sub-category, including parent-category filtering for financial sub-categories.

**Architecture:** Continue using the generic MongoDB `ReferenceData` collection. Add new `ReferenceDataType` values and an optional `parentCode` field; require `parentCode` only for `FINANCIAL_SUB_CATEGORY`. The existing `/reference-data` screen remains the single maintenance surface and gains parent category controls when the selected type is `FINANCIAL_SUB_CATEGORY`.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, Spring Security, Vue 3.x, Vue Router, MongoDB 6+, JUnit 5, Spring Test, Mockito, Vitest/Jest, Vue Testing Library.

## Global Constraints

- Java package structure remains under `com.church.operation` using `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`.
- Reference data is church-maintainable; bootstrap defaults must seed missing codes without overwriting changed existing records.
- `FINANCIAL_SUB_CATEGORY.parentCode` is required and must point to a `FINANCIAL_CATEGORY` code.
- Future financial transaction and budget screens must filter sub-category dropdowns by selected category.
- Do not hard delete reference data; use the `active` flag.
- Run backend tests with `cd backend && mvn test`.
- Run frontend verification with `cd frontend && npm run build`.

---

### Task 1: Extend Reference Data Model And Defaults

**Files:**
- Modify: `backend/src/main/java/com/church/operation/util/ReferenceDataType.java`
- Modify: `backend/src/main/java/com/church/operation/entity/ReferenceData.java`
- Modify: `backend/src/main/java/com/church/operation/dto/ReferenceDataRequest.java`
- Modify: `backend/src/main/java/com/church/operation/dto/ReferenceDataResponse.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java`

**Interfaces:**
- Produces enum values: `OFFERING_FUND_CATEGORY`, `FINANCIAL_CATEGORY`, `FINANCIAL_SUB_CATEGORY`.
- Produces field: `ReferenceData.parentCode`.
- Produces request/response property: `parentCode`.

- [ ] **Step 1: Write failing tests for new seeded defaults**

Add tests that expect `ReferenceDataService.seedDefaults()` to seed:

```text
OFFERING_FUND_CATEGORY:
  TITHE, THANKSGIVING, MISSION, BUILDING
FINANCIAL_CATEGORY:
  OFFICE, MINISTRY, FACILITY, MISSIONS
FINANCIAL_SUB_CATEGORY:
  OFFICE -> SUPPLIES, PRINTING
  FACILITY -> UTILITIES, REPAIR, RENT
  MINISTRY -> EVENT, MATERIALS
  MISSIONS -> LOCAL_OUTREACH, OVERSEAS_SUPPORT
```

Run: `cd backend && mvn test -Dtest=ReferenceDataServiceTest`

Expected: FAIL because the enum values and `parentCode` model do not exist.

- [ ] **Step 2: Add enum values and parentCode model fields**

Add enum values in `ReferenceDataType`. Add `private String parentCode;` with getter/setter in `ReferenceData`. Add `String parentCode` to `ReferenceDataRequest` and `ReferenceDataResponse`.

- [ ] **Step 3: Add default seeding with parentCode**

Add an overloaded seed helper:

```java
private void seed(ReferenceDataType type, String code, String label, int sortOrder, String parentCode)
```

The existing four-argument seed delegates with `parentCode = null`. Financial sub-category defaults pass the parent category code.

- [ ] **Step 4: Run focused backend test**

Run: `cd backend && mvn test -Dtest=ReferenceDataServiceTest`

Expected: PASS for default seeding tests.

### Task 2: Validate Parent Category Rules

**Files:**
- Modify: `backend/src/main/java/com/church/operation/repo/ReferenceDataRepository.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java`

**Interfaces:**
- Consumes: `ReferenceDataType.FINANCIAL_SUB_CATEGORY`.
- Produces validation: sub-category requires parent `FINANCIAL_CATEGORY`.
- Produces query support for future dropdowns.

- [ ] **Step 1: Write failing validation tests**

Add tests:

```text
create FINANCIAL_SUB_CATEGORY with blank parentCode -> IllegalArgumentException("Parent financial category is required for financial sub-category.")
create FINANCIAL_SUB_CATEGORY with unknown parentCode -> IllegalArgumentException("Parent financial category was not found.")
create FINANCIAL_CATEGORY with parentCode -> saved parentCode is null
```

Run: `cd backend && mvn test -Dtest=ReferenceDataServiceTest`

Expected: FAIL because validation is not implemented.

- [ ] **Step 2: Add repository query**

Add:

```java
List<ReferenceData> findByTypeAndParentCodeAndActiveTrueOrderBySortOrderAscLabelAsc(ReferenceDataType type, String parentCode);
```

- [ ] **Step 3: Implement validation**

Normalize `parentCode` to uppercase. Require it for `FINANCIAL_SUB_CATEGORY`. Verify `referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, parentCode)` returns a record. Clear `parentCode` for all other types.

- [ ] **Step 4: Run focused backend test**

Run: `cd backend && mvn test -Dtest=ReferenceDataServiceTest`

Expected: PASS.

### Task 3: Add Filtered Reference Data API

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReferenceDataController.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataService.java`
- Test: `backend/src/test/java/com/church/operation/service/ReferenceDataServiceTest.java`

**Interfaces:**
- Produces API: `GET /api/reference-data/FINANCIAL_SUB_CATEGORY?parentCode=OFFICE`.
- Existing API remains: `GET /api/reference-data/{type}`.

- [ ] **Step 1: Write failing service test for filtered active sub-categories**

Expect `listActive(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "office")` to call the repository with normalized parent code `OFFICE`.

Run: `cd backend && mvn test -Dtest=ReferenceDataServiceTest`

Expected: FAIL because the overloaded method does not exist.

- [ ] **Step 2: Implement overloaded service method**

Add:

```java
public List<ReferenceData> listActive(ReferenceDataType type, String parentCode)
```

For `FINANCIAL_SUB_CATEGORY` with a nonblank parent code, return only active children for that parent. Otherwise, keep the existing active list behavior.

- [ ] **Step 3: Update controller query parameter**

Change `listReferenceData` to accept `@RequestParam(name = "parentCode", required = false) String parentCode` and call the overloaded service method.

- [ ] **Step 4: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

### Task 4: Update Reference Data Maintenance UI

**Files:**
- Modify: `frontend/src/api/referenceData.ts`
- Modify: `frontend/src/views/ReferenceDataView.vue`
- Test: frontend type-check/build.

**Interfaces:**
- Consumes backend `parentCode` field and `ReferenceDataType` values.
- Produces type selector options for all five reference data types.
- Produces parent category selector for `FINANCIAL_SUB_CATEGORY`.

- [ ] **Step 1: Extend frontend reference data types**

Add union values:

```ts
export type ReferenceDataType =
  | 'GROUP_CODE'
  | 'MEMBERSHIP_STATUS'
  | 'OFFERING_FUND_CATEGORY'
  | 'FINANCIAL_CATEGORY'
  | 'FINANCIAL_SUB_CATEGORY';
```

Add `parentCode?: string` to `ReferenceDataOption` and `ReferenceDataPayload`.

- [ ] **Step 2: Add type options in `ReferenceDataView.vue`**

Replace hardcoded two-option selects with an array containing:

```ts
[
  { type: 'GROUP_CODE', label: 'Group code' },
  { type: 'MEMBERSHIP_STATUS', label: 'Membership status' },
  { type: 'OFFERING_FUND_CATEGORY', label: 'Offering fund/category' },
  { type: 'FINANCIAL_CATEGORY', label: 'Financial category' },
  { type: 'FINANCIAL_SUB_CATEGORY', label: 'Financial sub-category' },
]
```

- [ ] **Step 3: Add parent category dropdown**

When `form.type === 'FINANCIAL_SUB_CATEGORY'`, show a required `Parent category` dropdown loaded from `listReferenceData('FINANCIAL_CATEGORY')`. Include `parentCode` in create/update payloads.

- [ ] **Step 4: Show parent category in table**

Add a `Parent` column that displays `option.parentCode || '-'`.

- [ ] **Step 5: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

### Task 5: Verify Full Stack

**Files:**
- No source edits expected.
- Test: backend, frontend, Docker Compose.

- [ ] **Step 1: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

- [ ] **Step 3: Rebuild local Docker stack**

Run: `docker compose up -d --build`

Expected:

- MongoDB, backend, and frontend containers are running.
- `http://localhost:8080/actuator/health` returns `UP`.
- `http://localhost:5173` returns HTTP 200.

- [ ] **Step 4: Manual UI check**

Open `/reference-data` after login and confirm:

- Offering fund/category can be selected and maintained.
- Financial category can be selected and maintained.
- Financial sub-category shows a parent category dropdown.
- Created financial sub-categories display their parent code in the table.

## Self-Review

Spec coverage:

- Covers offering fund/category reference data.
- Covers financial category and parent-linked financial sub-category reference data.
- Covers filtered sub-category behavior required by future financial transaction and budget forms.
- Covers seed defaults without overwriting church-maintained records.

Placeholder scan:

- No placeholder markers remain.
- Each task has concrete files, commands, expected outcomes, and exact behavior.

Type consistency:

- `parentCode` is used consistently in backend entity, DTOs, service, API, and frontend payloads.
- `FINANCIAL_SUB_CATEGORY` is consistently the only type that requires `parentCode`.
