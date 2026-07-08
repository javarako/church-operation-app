# Offering Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Offering Management so Admin/Treasurer users can record member, anonymous, and group offerings, with each offering creating a linked income financial transaction immediately.

**Architecture:** Add `Offering` and minimal `FinancialTransaction` Mongo documents with repositories and DTOs under the existing `com.church.operation` package structure. `OfferingService` owns validation and the linked income creation flow, while `OfferingController` exposes list/create APIs. Vue adds a real `/offerings` view that uses reference data and member search.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, Vue 3.x, Vue Router, MongoDB 6+, JUnit 5, Mockito, Spring Test, Vitest/Jest, Vue Testing Library.

## Global Constraints

- Java package structure remains under `com.church.operation` using `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`.
- `offeringSunday` defaults to the offering date when the offering date is Sunday; otherwise defaults to the coming Sunday after the offering date.
- `offeringSunday` remains editable by Admin/Treasurer users.
- `fundCategory` must match active `OFFERING_FUND_CATEGORY` reference data.
- Member offerings require `memberId`; anonymous/group offerings require `giverLabel`.
- Offering-created income transactions use `type = INCOME`, `sourceType = OFFERING`, and `sourceId = offering.id`.
- Backend verification command: `cd backend && mvn test`.
- Frontend verification command: `cd frontend && npm run build`.

---

## File Structure

- Create `backend/src/main/java/com/church/operation/util/GivingType.java`: enum `MEMBER`, `ANONYMOUS`, `GROUP`.
- Create `backend/src/main/java/com/church/operation/util/FinancialTransactionType.java`: enum `INCOME`, `EXPENSE`.
- Create `backend/src/main/java/com/church/operation/util/FinancialSourceType.java`: enum `OFFERING`, `MANUAL`.
- Create `backend/src/main/java/com/church/operation/entity/Offering.java`: Mongo offering document.
- Create `backend/src/main/java/com/church/operation/entity/FinancialTransaction.java`: minimal Mongo finance document for offering-created income.
- Create `backend/src/main/java/com/church/operation/dto/OfferingRequest.java`: create offering payload.
- Create `backend/src/main/java/com/church/operation/dto/OfferingResponse.java`: offering API response.
- Create `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`: offering list queries.
- Create `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`: transaction persistence.
- Modify `backend/src/main/java/com/church/operation/repo/MemberRepository.java`: ensure `findById` is available through `MongoRepository`.
- Modify `backend/src/main/java/com/church/operation/repo/ReferenceDataRepository.java`: use existing active/type query for fund validation.
- Create `backend/src/main/java/com/church/operation/service/OfferingService.java`: validation, list, create offering + linked income.
- Create `backend/src/main/java/com/church/operation/rest/OfferingController.java`: `/api/offerings`.
- Create `backend/src/test/java/com/church/operation/service/OfferingServiceTest.java`: service behavior tests.
- Create `frontend/src/api/offerings.ts`: offering API client.
- Create `frontend/src/views/OfferingsView.vue`: offering list and record form.
- Modify `frontend/src/router/index.ts`: route `/offerings` to the real view.

---

### Task 1: Add Offering And Finance Domain Types

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/GivingType.java`
- Create: `backend/src/main/java/com/church/operation/util/FinancialTransactionType.java`
- Create: `backend/src/main/java/com/church/operation/util/FinancialSourceType.java`
- Create: `backend/src/main/java/com/church/operation/entity/Offering.java`
- Create: `backend/src/main/java/com/church/operation/entity/FinancialTransaction.java`
- Create: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Create: `backend/src/main/java/com/church/operation/repo/FinancialTransactionRepository.java`

**Interfaces:**
- Produces `GivingType { MEMBER, ANONYMOUS, GROUP }`.
- Produces `Offering` with `id`, `givingType`, `memberId`, `giverLabel`, `giverDisplayName`, `offeringDate`, `offeringSunday`, `fundCategory`, `amount`, `paymentMethod`, `memo`, `incomeTransactionId`, `createdBy`, `createdAt`.
- Produces `FinancialTransaction` with `id`, `type`, `transactionDate`, `amount`, `category`, `subCategory`, `sourceType`, `sourceId`, `memo`, `createdBy`, `createdAt`.

- [ ] **Step 1: Add enum files**

Create:

```java
package com.church.operation.util;

public enum GivingType {
    MEMBER,
    ANONYMOUS,
    GROUP
}
```

```java
package com.church.operation.util;

public enum FinancialTransactionType {
    INCOME,
    EXPENSE
}
```

```java
package com.church.operation.util;

public enum FinancialSourceType {
    OFFERING,
    MANUAL
}
```

- [ ] **Step 2: Add `Offering` document**

Create `Offering.java` with:

```java
@Document("offerings")
public class Offering {
    @Id private String id;
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
    // standard getters and setters
}
```

- [ ] **Step 3: Add `FinancialTransaction` document**

Create `FinancialTransaction.java` with:

```java
@Document("financialTransactions")
public class FinancialTransaction {
    @Id private String id;
    private FinancialTransactionType type;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String category;
    private String subCategory;
    private FinancialSourceType sourceType;
    private String sourceId;
    private String memo;
    private String createdBy;
    private Instant createdAt;
    // standard getters and setters
}
```

- [ ] **Step 4: Add repositories**

Create:

```java
public interface OfferingRepository extends MongoRepository<Offering, String> {
    List<Offering> findAllByOrderByOfferingDateDescCreatedAtDesc();
}
```

```java
public interface FinancialTransactionRepository extends MongoRepository<FinancialTransaction, String> {
}
```

- [ ] **Step 5: Compile backend**

Run: `cd backend && mvn test -DskipTests`

Expected: compile succeeds.

---

### Task 2: Add Offering DTOs And Service Tests

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/OfferingRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/OfferingResponse.java`
- Create: `backend/src/test/java/com/church/operation/service/OfferingServiceTest.java`

**Interfaces:**
- Consumes domain types from Task 1.
- Produces `OfferingRequest(GivingType givingType, String memberId, String giverLabel, LocalDate offeringDate, LocalDate offeringSunday, String fundCategory, BigDecimal amount, String paymentMethod, String memo)`.
- Produces `OfferingResponse.from(Offering offering)`.

- [ ] **Step 1: Add request/response records**

Create `OfferingRequest`:

```java
public record OfferingRequest(
    GivingType givingType,
    String memberId,
    String giverLabel,
    LocalDate offeringDate,
    LocalDate offeringSunday,
    String fundCategory,
    BigDecimal amount,
    String paymentMethod,
    String memo
) {}
```

Create `OfferingResponse`:

```java
public record OfferingResponse(
    String id,
    GivingType givingType,
    String memberId,
    String giverLabel,
    String giverDisplayName,
    LocalDate offeringDate,
    LocalDate offeringSunday,
    String fundCategory,
    BigDecimal amount,
    String paymentMethod,
    String memo,
    String incomeTransactionId,
    String createdBy,
    Instant createdAt
) {
    public static OfferingResponse from(Offering offering) {
        return new OfferingResponse(
            offering.getId(),
            offering.getGivingType(),
            offering.getMemberId(),
            offering.getGiverLabel(),
            offering.getGiverDisplayName(),
            offering.getOfferingDate(),
            offering.getOfferingSunday(),
            offering.getFundCategory(),
            offering.getAmount(),
            offering.getPaymentMethod(),
            offering.getMemo(),
            offering.getIncomeTransactionId(),
            offering.getCreatedBy(),
            offering.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Write failing service tests**

Create `OfferingServiceTest` with tests:

```java
@ExtendWith(MockitoExtension.class)
class OfferingServiceTest {
    @Mock OfferingRepository offeringRepository;
    @Mock FinancialTransactionRepository financialTransactionRepository;
    @Mock MemberRepository memberRepository;
    @Mock ReferenceDataRepository referenceDataRepository;

    @Test
    void createsMemberOfferingAndLinkedIncomeTransaction() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        Member giver = member("member-id", "giver@example.com", Role.MEMBER);
        giver.setDisplayName("Grace Kim");
        OfferingRequest request = request(GivingType.MEMBER, "member-id", null);

        when(memberRepository.findById("member-id")).thenReturn(Optional.of(giver));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> {
            Offering offering = invocation.getArgument(0);
            if (offering.getId() == null) {
                offering.setId("offering-id");
            }
            return offering;
        });
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction transaction = invocation.getArgument(0);
            transaction.setId("txn-id");
            return transaction;
        });

        OfferingService service = service();

        Offering saved = service.createOffering(actor, request);

        assertThat(saved.getId()).isEqualTo("offering-id");
        assertThat(saved.getMemberId()).isEqualTo("member-id");
        assertThat(saved.getGiverDisplayName()).isEqualTo("Grace Kim");
        assertThat(saved.getIncomeTransactionId()).isEqualTo("txn-id");
        verify(financialTransactionRepository).save(argThat(transaction ->
            transaction.getType() == FinancialTransactionType.INCOME
                && transaction.getSourceType() == FinancialSourceType.OFFERING
                && "offering-id".equals(transaction.getSourceId())
                && "TITHE".equals(transaction.getCategory())
        ));
    }

    @Test
    void createsAnonymousOfferingAndLinkedIncomeTransaction() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        OfferingRequest request = request(GivingType.ANONYMOUS, null, "Anonymous");

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> {
            Offering offering = invocation.getArgument(0);
            if (offering.getId() == null) {
                offering.setId("offering-id");
            }
            return offering;
        });
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction transaction = invocation.getArgument(0);
            transaction.setId("txn-id");
            return transaction;
        });

        Offering saved = service().createOffering(actor, request);

        assertThat(saved.getGivingType()).isEqualTo(GivingType.ANONYMOUS);
        assertThat(saved.getGiverLabel()).isEqualTo("Anonymous");
        assertThat(saved.getMemberId()).isNull();
        assertThat(saved.getIncomeTransactionId()).isEqualTo("txn-id");
    }

    @Test
    void rejectsMemberOfferingWithoutMemberId() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = request(GivingType.MEMBER, " ", null);

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Member is required for member offering.");
    }

    @Test
    void rejectsAnonymousOfferingWithoutLabel() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = request(GivingType.GROUP, null, " ");

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Giver label is required for anonymous or group offering.");
    }

    @Test
    void rejectsInvalidAmount() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = new OfferingRequest(
            GivingType.ANONYMOUS,
            null,
            "Anonymous",
            LocalDate.of(2026, 7, 8),
            null,
            "TITHE",
            BigDecimal.ZERO,
            "Cash",
            null
        );

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Offering amount must be greater than zero.");
    }

    @Test
    void calculatesComingSundayWhenOfferingSundayIsBlank() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        OfferingRequest request = request(GivingType.ANONYMOUS, null, "Anonymous");

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> {
            Offering offering = invocation.getArgument(0);
            if (offering.getId() == null) {
                offering.setId("offering-id");
            }
            return offering;
        });
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction transaction = invocation.getArgument(0);
            transaction.setId("txn-id");
            return transaction;
        });

        Offering saved = service().createOffering(actor, request);

        assertThat(saved.getOfferingDate()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(saved.getOfferingSunday()).isEqualTo(LocalDate.of(2026, 7, 12));
    }

    private OfferingService service() {
        return new OfferingService(offeringRepository, financialTransactionRepository, memberRepository, referenceDataRepository);
    }

    private OfferingRequest request(GivingType givingType, String memberId, String giverLabel) {
        return new OfferingRequest(
            givingType,
            memberId,
            giverLabel,
            LocalDate.of(2026, 7, 8),
            null,
            "TITHE",
            new BigDecimal("25.00"),
            "Cash",
            "Sunday offering"
        );
    }

    private Member member(String id, String primaryEmail, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(primaryEmail);
        member.setRoles(Set.of(role));
        member.setActive(true);
        return member;
    }

    private ReferenceData activeReference(String code) {
        ReferenceData referenceData = new ReferenceData();
        referenceData.setType(ReferenceDataType.OFFERING_FUND_CATEGORY);
        referenceData.setCode(code);
        referenceData.setLabel(code);
        referenceData.setActive(true);
        return referenceData;
    }
}
```

Use assertions:

```java
assertThat(savedOffering.getIncomeTransactionId()).isEqualTo("txn-id");
assertThat(savedTransaction.getType()).isEqualTo(FinancialTransactionType.INCOME);
assertThat(savedTransaction.getSourceType()).isEqualTo(FinancialSourceType.OFFERING);
assertThat(savedTransaction.getSourceId()).isEqualTo("offering-id");
```

- [ ] **Step 3: Run focused test to verify it fails**

Run: `cd backend && mvn test -Dtest=OfferingServiceTest`

Expected: FAIL because `OfferingService` does not exist.

---

### Task 3: Implement Offering Service

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/OfferingService.java`
- Modify: `backend/src/test/java/com/church/operation/service/OfferingServiceTest.java`

**Interfaces:**
- Consumes `OfferingRequest`.
- Produces `List<Offering> listOfferings(Member actor)`.
- Produces `Offering createOffering(Member actor, OfferingRequest request)`.

- [ ] **Step 1: Add `OfferingService` constructor and public methods**

Create:

```java
@Service
public class OfferingService {
    private final OfferingRepository offeringRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final MemberRepository memberRepository;
    private final ReferenceDataRepository referenceDataRepository;

    public OfferingService(
        OfferingRepository offeringRepository,
        FinancialTransactionRepository financialTransactionRepository,
        MemberRepository memberRepository,
        ReferenceDataRepository referenceDataRepository
    ) {
        this.offeringRepository = offeringRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.memberRepository = memberRepository;
        this.referenceDataRepository = referenceDataRepository;
    }

    public List<Offering> listOfferings(Member actor) {
        requireOfferingAccess(actor, false);
        return offeringRepository.findAllByOrderByOfferingDateDescCreatedAtDesc();
    }

    public Offering createOffering(Member actor, OfferingRequest request) {
        requireOfferingAccess(actor, true);
        // implemented in next step
    }
}
```

- [ ] **Step 2: Implement validation and Sunday calculation**

Add helpers:

```java
private LocalDate resolveOfferingSunday(LocalDate offeringDate, LocalDate requestedSunday) {
    if (requestedSunday != null) {
        return requestedSunday;
    }
    int daysUntilSunday = DayOfWeek.SUNDAY.getValue() - offeringDate.getDayOfWeek().getValue();
    if (daysUntilSunday < 0) {
        daysUntilSunday += 7;
    }
    return offeringDate.plusDays(daysUntilSunday);
}
```

Validation messages:

- `"You do not have permission to manage offerings."`
- `"Giving type is required."`
- `"Offering date is required."`
- `"Offering amount must be greater than zero."`
- `"Offering fund/category is required."`
- `"Offering fund/category was not found."`
- `"Member is required for member offering."`
- `"Offering member was not found."`
- `"Giver label is required for anonymous or group offering."`

- [ ] **Step 3: Implement create flow**

Implementation must:

1. Save the offering first.
2. Save an income transaction referencing the offering id.
3. Save the offering again with `incomeTransactionId`.

Use:

```java
transaction.setType(FinancialTransactionType.INCOME);
transaction.setSourceType(FinancialSourceType.OFFERING);
transaction.setSourceId(savedOffering.getId());
transaction.setCategory(savedOffering.getFundCategory());
```

- [ ] **Step 4: Run focused service tests**

Run: `cd backend && mvn test -Dtest=OfferingServiceTest`

Expected: PASS.

---

### Task 4: Add Offering REST API

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/OfferingController.java`
- Test through service tests and full backend test run.

**Interfaces:**
- Produces `GET /api/offerings`.
- Produces `POST /api/offerings`.

- [ ] **Step 1: Add controller**

Create:

```java
@RestController
@RequestMapping("/api/offerings")
public class OfferingController {
    private final OfferingService offeringService;

    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @GetMapping
    List<OfferingResponse> listOfferings(Authentication authentication) {
        return offeringService.listOfferings(actor(authentication)).stream()
            .map(OfferingResponse::from)
            .toList();
    }

    @PostMapping
    OfferingResponse createOffering(Authentication authentication, @RequestBody OfferingRequest request) {
        return OfferingResponse.from(offeringService.createOffering(actor(authentication), request));
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

### Task 5: Add Frontend Offering API And Route

**Files:**
- Create: `frontend/src/api/offerings.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Produces `listOfferings(): Promise<Offering[]>`.
- Produces `createOffering(payload: OfferingPayload): Promise<Offering>`.

- [ ] **Step 1: Add API client**

Create:

```ts
import { getJson, postJson } from './http';

export type GivingType = 'MEMBER' | 'ANONYMOUS' | 'GROUP';

export interface Offering {
  id: string;
  givingType: GivingType;
  memberId?: string;
  giverLabel?: string;
  giverDisplayName?: string;
  offeringDate: string;
  offeringSunday: string;
  fundCategory: string;
  amount: number;
  paymentMethod?: string;
  memo?: string;
  incomeTransactionId?: string;
}

export interface OfferingPayload {
  givingType: GivingType;
  memberId?: string;
  giverLabel?: string;
  offeringDate: string;
  offeringSunday?: string;
  fundCategory: string;
  amount: number;
  paymentMethod?: string;
  memo?: string;
}

export function listOfferings() {
  return getJson<Offering[]>('/api/offerings');
}

export function createOffering(payload: OfferingPayload) {
  return postJson<OfferingPayload, Offering>('/api/offerings', payload);
}
```

- [ ] **Step 2: Route to real view**

Modify `frontend/src/router/index.ts`:

```ts
import OfferingsView from '../views/OfferingsView.vue';
```

Replace the placeholder offerings route:

```ts
{ path: '/offerings', component: OfferingsView, meta: { roles: financeRoles } },
```

- [ ] **Step 3: Run frontend build to verify missing view failure**

Run: `cd frontend && npm run build`

Expected: FAIL because `OfferingsView.vue` does not exist yet.

---

### Task 6: Build Offering Management UI

**Files:**
- Create: `frontend/src/views/OfferingsView.vue`
- Uses: `frontend/src/api/offerings.ts`, `frontend/src/api/referenceData.ts`, `frontend/src/api/members.ts`

**Interfaces:**
- Consumes `listReferenceData('OFFERING_FUND_CATEGORY')`.
- Consumes `listMembers(search)`.
- Consumes `createOffering(payload)`.

- [ ] **Step 1: Create view shell**

Create a Vue view using the existing `workspace`, `page-header`, `two-column`, `panel`, `toolbar`, `table-wrap`, and `form-grid` classes.

Required screen elements:

- Header `Offerings`.
- Filter toolbar with fund/category and giving type.
- Total strip.
- Table columns: Date, Sunday, Giver, Fund/category, Amount, Payment, Linked income.
- Record form on the right.

- [ ] **Step 2: Add form state and Sunday calculation**

Use this TypeScript helper:

```ts
function calculateComingSunday(dateValue: string) {
  const date = new Date(`${dateValue}T00:00:00`);
  const day = date.getDay();
  const daysUntilSunday = day === 0 ? 0 : 7 - day;
  date.setDate(date.getDate() + daysUntilSunday);
  return date.toISOString().slice(0, 10);
}
```

When `offeringDate` changes, update `offeringSunday` using the helper. Keep `offeringSunday` bound to an editable date input.

- [ ] **Step 3: Add giving type behavior**

Rules:

- `MEMBER`: show member search/select. Payload includes `memberId` and omits `giverLabel`.
- `ANONYMOUS`: show label input defaulting to `Anonymous`. Payload includes `giverLabel`.
- `GROUP`: show label input with no default. Payload includes `giverLabel`.

- [ ] **Step 4: Add save behavior**

On submit:

1. Clear error/saved message.
2. Send payload through `createOffering`.
3. Reload offerings.
4. Reset amount, memo, payment method, member, and label.
5. Show `Saved`.

- [ ] **Step 5: Add amount handling**

Use `<input v-model.number="form.amount" type="number" min="0.01" step="0.01" required />`.

Format table amounts with:

```ts
function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value);
}
```

- [ ] **Step 6: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

---

### Task 7: Full Verification And Local Docker Refresh

**Files:**
- No source edits expected.

**Interfaces:**
- Verifies backend and frontend are both healthy.

- [ ] **Step 1: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

- [ ] **Step 3: Rebuild local Docker app**

Run: `docker compose up -d --build`

Expected: Mongo, backend, and frontend containers are running.

- [ ] **Step 4: Check local frontend**

Run: `curl -I http://localhost:5173`

Expected: `HTTP/1.1 200 OK`.

---

## Self-Review

- Spec coverage: Offering storage, linked income transaction, member/anonymous/group support, editable auto-calculated offering Sunday, fund/category reference data, list/create API, route, UI, and verification are covered.
- Placeholder scan: no red-flag placeholders or undefined implementation shortcuts remain.
- Type consistency: `GivingType`, `OfferingRequest`, `OfferingResponse`, `OfferingService.createOffering`, and `incomeTransactionId` are named consistently across backend and frontend tasks.
