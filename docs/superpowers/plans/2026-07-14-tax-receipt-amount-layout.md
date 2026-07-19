# Official Tax Receipt Amount Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show one prominent `Amount` value on each half-page official tax receipt and remove the five superseded labels.

**Architecture:** Keep the receipt entity and calculation service unchanged for audit compatibility. Modify only PDF rendering coordinates and the PDF text-extraction regression test.

**Tech Stack:** Java 21, Spring Boot 4, Apache PDFBox, JUnit 5, AssertJ

## Global Constraints

- Preserve the one-page US Letter document with two identical half-page receipt copies.
- Keep offering number and tax calculation fields in stored receipt data; remove them only from visible PDF output.
- Both copies must retain church identity, receipt metadata, donor identity/address, thank-you note, signature, and CRA information.

---

### Task 1: Simplify And Enlarge The PDF Donation Section

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java`

**Interfaces:**
- Consumes: `TaxReceipt.getGiftAmount()` as the retained donation total.
- Produces: `TaxReceiptPdfService.render(TaxReceipt)` with two visible `Amount: <currency>` values and none of the removed labels.

- [ ] **Step 1: Write the failing PDF extraction assertions**

Replace the amount assertions with:

```java
assertOccursTwice(text, "Amount: $1,245.50");
assertThat(text).doesNotContain(
    "Offering number:",
    "Amount of gift:",
    "Advantage amount:",
    "Advantage description:",
    "Eligible amount:"
);
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -Dtest=TaxReceiptPdfServiceTest#rendersTwoIdenticalOfficialReceiptsOnOneLetterPage test`

Expected: FAIL because the PDF still contains the old five labels and does not contain `Amount: $1,245.50`.

- [ ] **Step 3: Implement the compact PDF layout**

In `renderReceipt`, remove the five old `write(...)` calls. Render only:

```java
write(stream, "Amount: " + money(receipt.getGiftAmount()), LEFT, originY - 187f, BOLD, 11f);
```

Apply a shared 2-point increase to every visible PDF text size. Move the thank-you heading and wrapped note upward to use the released vertical space while retaining the existing signature coordinates.

- [ ] **Step 4: Run PDF tests and backend verification**

Run: `mvn -Dtest=TaxReceiptPdfServiceTest test`

Expected: 2 tests pass with zero failures.

Run: `mvn -Dtest=TaxReceiptPdfServiceTest,TaxReceiptServiceTest test`

Expected: all selected receipt tests pass with zero failures.

- [ ] **Step 5: Inspect the generated PDF structure and formatting**

Confirm the extracted text contains two identical `Amount` entries, no removed labels, one US Letter page, and all required identity/signature/CRA content. Run `git diff --check` and confirm exit code 0.
