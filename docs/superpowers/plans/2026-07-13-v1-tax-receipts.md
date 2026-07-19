# v1.0 Official Tax Receipts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-offering tax extract rows with annual member summaries and generate immutable, CRA-oriented, two-up Letter PDF receipts individually or in batches.

**Architecture:** Persist `TaxReceipt` snapshots and per-year atomic counters. `TaxReceiptService` owns eligibility/lifecycle, while `TaxReceiptPdfService` renders from snapshots so re-downloads never change.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB, Apache PDFBox, Spring Security, Vue 3, JUnit 5, Mockito, PDFBox text extraction, Vitest, Vue Testing Library.

## Global Constraints

- Calendar-year cash gifts only; no donor advantage.
- One summary row per member; remove Giving Date and Fund/Category.
- Total offering amount equals eligible amount; UI shows only total.
- Receipt serial format is `YYYY-000001`.
- Letter page contains two identical unlabeled half-page receipts with a dashed cut line.
- PDF includes all configuration and CRA fields from the approved spec.
- Treasurer signature is a blank line with configured Treasurer name/title.
- Default thank-you note text must match the specification exactly.
- Only Admin/Treasurer can issue, void, replace, or download official receipts.

---

### Task 1: Add Receipt Configuration And Persistent Models

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`
- Create: `backend/src/main/java/com/church/operation/entity/TaxReceipt.java`
- Create: `backend/src/main/java/com/church/operation/entity/TaxReceiptCounter.java`
- Create: `backend/src/main/java/com/church/operation/util/TaxReceiptStatus.java`
- Create: `backend/src/main/java/com/church/operation/repo/TaxReceiptRepository.java`
- Create: `backend/src/main/java/com/church/operation/repo/TaxReceiptCounterRepository.java`
- Create: `backend/src/test/java/com/church/operation/config/ChurchInformationPropertiesTest.java`

**Interfaces:**
- Produces: church website/registration/issue-location properties, receipt snapshot entities and repositories.

- [ ] **Step 1: Write failing property-binding test**

Assert configured values bind to:

```java
information.charityRegistrationNumber();
information.receiptIssueLocation();
information.website();
```

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=ChurchInformationPropertiesTest test`

Expected: missing accessor compilation failures.

- [ ] **Step 3: Add PDF dependency and configuration**

Add Apache PDFBox 3.0.8, the current Apache release verified during planning, and environment-backed properties:

```xml
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.8</version>
</dependency>
```

```yaml
charity-registration-number: ${CHURCH_CHARITY_REGISTRATION_NUMBER:}
receipt-issue-location: ${CHURCH_RECEIPT_ISSUE_LOCATION:}
website: ${CHURCH_WEBSITE:}
```

- [ ] **Step 4: Define receipt snapshot and indexes**

`TaxReceipt` contains: `id`, `receiptNumber`, `status`, `taxYear`, `issueDate`, `issuedByMemberId`, `memberId`, `offeringNumber`, donor name/address/email, church name/address/registration number/website/issue location/Treasurer, gift/eligible/advantage amounts, advantage description, thank-you note, source offering IDs/checksum, `voidReason`, `voidedAt`, `voidedByMemberId`, `replacesReceiptId`, `replacementReceiptId`, `createdAt`, and `updatedAt`. Mark `receiptNumber` unique. Add compound indexes for `(taxYear, offeringNumber)` and `(memberId, taxYear)`.

`TaxReceiptCounter` contains `_id = taxYear` and `nextSequence`.

- [ ] **Step 5: Run property tests and compile**

Run: `cd backend && mvn -DskipTests compile`

Expected: success.

Run: `cd backend && mvn -Dtest=ChurchInformationPropertiesTest test`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java backend/src/main/java/com/church/operation/rest/ChurchInformationController.java backend/src/main/java/com/church/operation/entity/TaxReceipt.java backend/src/main/java/com/church/operation/entity/TaxReceiptCounter.java backend/src/main/java/com/church/operation/util/TaxReceiptStatus.java backend/src/main/java/com/church/operation/repo/TaxReceiptRepository.java backend/src/main/java/com/church/operation/repo/TaxReceiptCounterRepository.java backend/src/test/java/com/church/operation/config/ChurchInformationPropertiesTest.java
git commit -m "feat: add tax receipt persistence"
```

### Task 2: Implement Annual Summary, Eligibility, And Atomic Serial Allocation

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/TaxReceiptSummaryRow.java`
- Create: `backend/src/main/java/com/church/operation/dto/TaxReceiptIssueRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/TaxReceiptValidationError.java`
- Create: `backend/src/main/java/com/church/operation/service/TaxReceiptCounterService.java`
- Create: `backend/src/main/java/com/church/operation/service/TaxReceiptService.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReportService.java`
- Modify: `backend/src/main/java/com/church/operation/repo/OfferingRepository.java`
- Test: `backend/src/test/java/com/church/operation/service/TaxReceiptServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/TaxReceiptCounterServiceTest.java`

**Interfaces:**
- Produces: `summary(Member,int,String)`, `issue(Member,int,String,String)`, `issueBatch(Member,int,String)`, `voidReceipt`, and `replaceReceipt`.

- [ ] **Step 1: Write failing summary/lifecycle tests**

```java
@Test
void summaryGroupsMemberOfferingsByCalendarYearAndSortsOfferingNumber() {
    when(offeringRepository.findReceiptEligibleBetween(date(2026, 1, 1), date(2026, 12, 31)))
        .thenReturn(List.of(memberOffering("1002", "30.00"), memberOffering("1001", "20.00"),
            memberOffering("1001", "25.00")));
    assertThat(service.summary(treasurer(), 2026, null))
        .extracting(TaxReceiptSummaryRow::offeringNumber, TaxReceiptSummaryRow::totalAmount)
        .containsExactly(tuple("1001", money("45.00")), tuple("1002", money("30.00")));
}

@Test
void issuanceRejectsMissingDonorAddressBeforeAllocatingSerial() {
    when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.of(memberWithoutAddress()));
    assertThatThrownBy(() -> service.issue(treasurer(), 2026, "1001", DEFAULT_NOTE))
        .isInstanceOf(ReceiptValidationException.class)
        .hasMessageContaining("donor address");
    verifyNoInteractions(counterService);
}

@Test
void reissueReturnsExistingSnapshotWithoutNewSerial() {
    when(receiptRepository.findActiveByTaxYearAndOfferingNumber(2026, "1001"))
        .thenReturn(Optional.of(issuedReceipt("2026-000001")));
    assertThat(service.issue(treasurer(), 2026, "1001", DEFAULT_NOTE).receiptNumber())
        .isEqualTo("2026-000001");
    verifyNoInteractions(counterService);
}
```

Add focused tests with the same arrange/act/assert structure for anonymous/deleted exclusion, immutable source/church/donor/note snapshots, source-checksum mismatch warnings, and void/replacement links with a newly allocated serial.

- [ ] **Step 2: Write failing atomic-counter test**

Mock `MongoTemplate.findAndModify` with `returnNew(true).upsert(true)` and assert `2026-000001`, then `2026-000002`.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `cd backend && mvn -Dtest=TaxReceiptServiceTest,TaxReceiptCounterServiceTest test`

Expected: compilation failures.

- [ ] **Step 4: Implement calendar-year aggregation and validation**

Use January 1 through December 31 inclusive. Require member full name, offering number, and complete mailing address. Treat inactive members as eligible when valid offerings exist. Validate thank-you notes as plain text with `@Size(max = 500)`.

Source checksum input must be deterministic:

```java
String canonical = offerings.stream()
    .sorted(Comparator.comparing(Offering::getId))
    .map(o -> o.getId() + ":" + o.getAmount().toPlainString())
    .collect(Collectors.joining("|"));
```

- [ ] **Step 5: Implement atomic allocation and lifecycle**

Use `MongoTemplate.findAndModify` on `_id = taxYear`. Format with six digits. Pre-validate all members before batch allocation; return structured validation errors without partial issuance.

- [ ] **Step 6: Run focused tests**

Run: `cd backend && mvn -Dtest=TaxReceiptServiceTest,TaxReceiptCounterServiceTest test`

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/church/operation/dto/TaxReceiptSummaryRow.java backend/src/main/java/com/church/operation/dto/TaxReceiptIssueRequest.java backend/src/main/java/com/church/operation/dto/TaxReceiptValidationError.java backend/src/main/java/com/church/operation/service/TaxReceiptCounterService.java backend/src/main/java/com/church/operation/service/TaxReceiptService.java backend/src/main/java/com/church/operation/service/ReportService.java backend/src/main/java/com/church/operation/repo/OfferingRepository.java backend/src/test/java/com/church/operation/service/TaxReceiptServiceTest.java backend/src/test/java/com/church/operation/service/TaxReceiptCounterServiceTest.java
git commit -m "feat: add annual tax receipt lifecycle"
```

### Task 3: Render Two-Up Letter PDFs

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java`
- Test: `backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java`

**Interfaces:**
- Consumes: immutable `TaxReceipt`.
- Produces: `render(TaxReceipt receipt): byte[]`.

- [ ] **Step 1: Write failing PDF structure tests**

Use PDFBox to assert:

```java
assertThat(document.getNumberOfPages()).isEqualTo(1);
assertThat(page.getMediaBox().getWidth()).isEqualTo(612f);
assertThat(page.getMediaBox().getHeight()).isEqualTo(792f);
assertThat(extractedText.split("Official Receipt for Income Tax Purposes", -1)).hasSize(3);
assertThat(extractedText.split("2026-000001", -1)).hasSize(3);
```

Assert every mandatory value and the default/custom thank-you note occurs twice.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=TaxReceiptPdfServiceTest test`

Expected: missing class compilation failure.

- [ ] **Step 3: Implement one reusable half-page renderer**

```java
private void renderReceipt(PDPageContentStream stream, TaxReceipt receipt, float originY) throws IOException {
    writeCentered(stream, "Official Receipt for Income Tax Purposes", originY - 24, 14);
    writeChurchIdentity(stream, receipt, originY - 46);
    writeReceiptIdentity(stream, receipt, originY - 104);
    writeDonorAndAmounts(stream, receipt, originY - 154);
    writeWrappedNote(stream, receipt.getThankYouNote(), originY - 252, 500);
    drawSignatureLine(stream, receipt.getTreasurerName(), "Treasurer", originY - 336);
    writeCraReference(stream, "Canada Revenue Agency", "canada.ca/charities-giving", originY - 370);
}
```

Call it for top and bottom with identical snapshot content. Draw a dashed line at `y = 396`. Use built-in fonts and wrap plain text to fixed widths; reject overlong note before issuance rather than clipping.

- [ ] **Step 4: Add logo fallback and blank signature line**

Load the configured classpath logo if available. If absent, omit it without shifting required fields off-page. Draw, do not electronically sign, the signature line.

- [ ] **Step 5: Run PDF tests**

Run: `cd backend && mvn -Dtest=TaxReceiptPdfServiceTest test`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java
git commit -m "feat: render two-up tax receipt PDFs"
```

### Task 4: Add Receipt APIs And Batch ZIP

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/ReportController.java`
- Create: `backend/src/main/java/com/church/operation/dto/VoidTaxReceiptRequest.java`
- Create: `backend/src/test/java/com/church/operation/rest/ReportControllerTest.java`
- Delete: `backend/src/main/java/com/church/operation/dto/OfficialTaxReportRow.java`
- Modify: `backend/src/test/java/com/church/operation/service/ReportServiceTest.java`

**Interfaces:**
- Produces: summary, issue, issue-batch, PDF, void, and replace endpoints from the design.

- [ ] **Step 1: Write failing controller tests**

Test explicit request parameter names, Admin/Treasurer access, Viewer forbidden, PDF headers, batch ZIP entry names, void reason required, and replacement response.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=ReportControllerTest test`

Expected: endpoint 404 failures.

- [ ] **Step 3: Implement endpoint mappings**

Remove the old `/api/reports/tax-return` endpoint and its per-offering DTO/tests. Use `ResponseEntity<byte[]>` with:

```java
.contentType(MediaType.APPLICATION_PDF)
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + number + ".pdf")
```

Batch uses standard `ZipOutputStream`, because receipt ZIPs do not contain the database and were not specified as password-encrypted.

- [ ] **Step 4: Run controller tests and commit**

Run: `cd backend && mvn -Dtest=ReportControllerTest test`

Expected: pass.

```bash
git add backend/src/main/java/com/church/operation/rest/ReportController.java backend/src/main/java/com/church/operation/dto/VoidTaxReceiptRequest.java backend/src/test/java/com/church/operation/rest/ReportControllerTest.java backend/src/test/java/com/church/operation/service/ReportServiceTest.java
git add -u backend/src/main/java/com/church/operation/dto/OfficialTaxReportRow.java
git commit -m "feat: expose official tax receipt downloads"
```

### Task 5: Replace Official Tax Report UI

**Files:**
- Modify: `frontend/src/api/http.ts`
- Rewrite tax types/functions in: `frontend/src/api/reports.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Rewrite relevant tests in: `frontend/src/views/ReportsView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: receipt APIs from Task 4.
- Produces: annual summary list, editable note, individual/batch PDF downloads, void/replace actions.

- [ ] **Step 1: Write failing UI tests**

Assert:

```ts
expect(screen.getByRole('columnheader', { name: 'Offering #' })).toBeTruthy();
expect(screen.queryByRole('columnheader', { name: 'Giving Date' })).toBeNull();
expect(screen.queryByRole('columnheader', { name: 'Fund / Category' })).toBeNull();
expect(screen.getByDisplayValue(DEFAULT_THANK_YOU_NOTE)).toBeTruthy();
```

Test individual download, batch download, missing-address errors, mismatch warning, void confirmation, and replacement.

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm test -- ReportsView.test.ts`

Expected: old report columns/actions fail assertions.

- [ ] **Step 3: Add generic POST Blob helper**

```ts
export async function postBlob<T>(path: string, body: T): Promise<Blob> {
  const response = await request(path, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return response.blob();
}
```

Preserve JSON error parsing when response is not OK.

- [ ] **Step 4: Implement annual summary UI**

Keep report selected-button highlighting. Use tax year and offering-number filters. Display only approved columns and sort returned data defensively by numeric-aware offering number.

- [ ] **Step 5: Implement note and lifecycle actions**

Export the exact default note constant. Disable issue actions while validation/download runs. Revoke generated download URLs after clicking.

- [ ] **Step 6: Run tests and build**

Run: `cd frontend && npm test -- ReportsView.test.ts`

Run: `cd frontend && npm run build`

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/http.ts frontend/src/api/reports.ts frontend/src/views/ReportsView.vue frontend/src/views/ReportsView.test.ts frontend/src/styles/main.css
git commit -m "feat: add annual official tax receipts UI"
```

### Task 6: Verify Receipt Slice

**Files:**
- Modify only for scoped verification fixes.

**Interfaces:**
- Produces: immutable receipt records used by archive/deletion plans.

- [ ] **Step 1: Run all automated tests**

Run: `cd backend && mvn test`

Run: `cd frontend && npm test && npm run build`

Expected: all pass.

- [ ] **Step 2: Generate real PDFs from sample data**

Verify one member and a batch. Render the PDF page to an image and visually inspect both halves, cut line, logo, wrapping, blank signature, and thank-you note.

- [ ] **Step 3: Test immutable lifecycle**

Issue a receipt, edit an offering, confirm mismatch warning, re-download unchanged original, void, then replace with next serial.

- [ ] **Step 4: Confirm verification leaves no unreviewed changes**

Run: `git diff --check`

Expected: no output. Route any defect back to its owning task with a failing regression test before changing implementation.
