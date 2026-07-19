# Tax Receipt Logo Position Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enlarge the tax receipt logo by 20% and vertically center both logo copies within their receipt header bands.

**Architecture:** Keep the existing PDFBox rendering flow. Replace the fixed logo Y offset with a calculation based on explicit header-band top and bottom offsets and the aspect-ratio-preserving rendered logo height.

**Tech Stack:** Java 21, Spring Boot 4, Apache PDFBox 3, JUnit 5, AssertJ

## Global Constraints

- Preserve the logo source aspect ratio.
- Set maximum logo bounds to 112.32 points wide and 68.64 points high.
- Apply identical relative geometry to the upper and lower half-page receipts.
- Position church information 10 points after the rendered logo's right edge.
- Keep the remaining receipt-detail positions unchanged.
- Leave all work uncommitted in the current `develop` checkout.

---

### Task 1: Enlarge And Center The Tax Receipt Logo

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java:26-101`
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java:25-77`

**Interfaces:**
- Consumes: `TaxReceiptPdfService.render(TaxReceipt): byte[]`
- Produces: two PDF image transforms with width `112.32`, aspect-ratio-derived height, X `30`, and matching relative vertical centers; church information begins at X `152.32`.

- [x] **Step 1: Write the failing PDF geometry assertions**

Update the size test to expect:

```java
assertThat(TaxReceiptPdfService.LOGO_MAX_WIDTH).isEqualTo(112.32f);
assertThat(TaxReceiptPdfService.LOGO_MAX_HEIGHT).isEqualTo(68.64f);
```

Update `embedsConfiguredClasspathLogo()` to assert:

```java
assertThat(transform[0]).isCloseTo(112.32f, offset(0.01f));
assertThat(transform[1]).isCloseTo(38.11f, offset(0.01f));
assertThat(imageTransforms).extracting(transform -> transform[3])
    .containsExactly(710.95f, 314.95f);
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=TaxReceiptPdfServiceTest test
```

Expected: failure because the current width is `93.6`, height is approximately `31.76`, and Y positions are `706` and `310`.

- [x] **Step 3: Implement centered aspect-ratio-preserving geometry**

In `TaxReceiptPdfService`, define:

```java
static final float LOGO_MAX_WIDTH = 112.32f;
static final float LOGO_MAX_HEIGHT = 68.64f;
private static final float HEADER_TOP_OFFSET = 34f;
private static final float HEADER_BOTTOM_OFFSET = 90f;
```

Replace the fixed draw position with:

```java
float scale = Math.min(LOGO_MAX_WIDTH / logo.getWidth(), LOGO_MAX_HEIGHT / logo.getHeight());
float renderedWidth = logo.getWidth() * scale;
float renderedHeight = logo.getHeight() * scale;
float headerCenterY = originY - ((HEADER_TOP_OFFSET + HEADER_BOTTOM_OFFSET) / 2f);
float logoY = headerCenterY - (renderedHeight / 2f);
stream.drawImage(logo, LEFT, logoY, renderedWidth, renderedHeight);
```

- [x] **Step 4: Run focused and complete backend verification**

Run:

```bash
cd backend
mvn -Dtest=TaxReceiptPdfServiceTest test
mvn test
```

Expected: `TaxReceiptPdfServiceTest` passes and the complete backend suite has zero failures or errors.

- [x] **Step 5: Rebuild and probe the local Docker application**

Run:

```bash
docker compose build backend
docker compose up -d backend
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/church-information
```

Expected: backend image builds, service starts, and the endpoint returns `200`.
