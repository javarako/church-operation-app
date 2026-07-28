# Runtime Church Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ADMIN-managed runtime church information and GridFS branding, immediate application refresh, effective report branding, and backend build-version display.

**Architecture:** Persist one `ChurchSettings` override document and logo/banner bytes in MongoDB GridFS. A resolver combines database overrides with existing `ChurchInformationProperties` defaults, and all UI/report consumers use that resolver. The frontend keeps one reactive church-information store so a successful ADMIN save or reset updates the menu and dashboard without relogin.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Security, Spring Data MongoDB, MongoDB GridFS, Apache PDFBox, Apache POI, Vue 3, TypeScript, Vitest, Vue Testing Library, JUnit 5, Mockito, Testcontainers.

## Global Constraints

- Only `ADMIN` may read editable settings, save settings, upload branding, or reset settings.
- All existing `/api/church-information` response fields remain compatible.
- `application.yml` and bundled branding remain reset and missing-data defaults.
- Accept only decoded PNG or JPEG images, at most 5 MB each, 8,000 pixels per dimension, and 40 megapixels total.
- Failed writes keep the current settings and images active.
- Full backup/restore includes the settings and GridFS files; fiscal archive/clean preserves them.
- No relogin or backend restart is required after save or reset.
- Previously generated PDF and Excel files do not change.
- Use the effective logo for newly generated tax receipts and quarterly/yearly financial workbooks.
- Show the backend build version in the form `v1.0.0` below `Church Operations`.
- Preserve unrelated dirty-worktree changes.

---

## File Structure

### Backend Domain And Resolution

- Create `backend/src/main/java/com/church/operation/entity/ChurchSettings.java`: singleton MongoDB override document.
- Create `backend/src/main/java/com/church/operation/repo/ChurchSettingsRepository.java`: persistence interface.
- Create `backend/src/main/java/com/church/operation/service/EffectiveChurchInformation.java`: immutable effective text/URL model.
- Create `backend/src/main/java/com/church/operation/service/ChurchInformationResolver.java`: database/default resolution and effective branding URLs.
- Create `backend/src/main/java/com/church/operation/service/ChurchBrandingService.java`: validated GridFS storage, retrieval, replacement, rollback, and default-resource loading.
- Create `backend/src/main/java/com/church/operation/service/ChurchSettingsService.java`: ADMIN mutation workflow and audit boundary.
- Create `backend/src/main/java/com/church/operation/service/ApplicationVersionProvider.java`: build metadata with development fallback.

### Backend API

- Create `backend/src/main/java/com/church/operation/dto/ChurchSettingsSaveRequest.java`.
- Create `backend/src/main/java/com/church/operation/dto/ChurchSettingsAdminResponse.java`.
- Create `backend/src/main/java/com/church/operation/rest/ChurchSettingsController.java`.
- Modify `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`.
- Modify `backend/src/main/java/com/church/operation/config/SecurityConfig.java` only if the effective image routes need explicit public-read compatibility.

### Backend Consumers

- Modify `backend/src/main/java/com/church/operation/service/TaxReceiptService.java`.
- Modify `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java`.
- Modify `backend/src/main/java/com/church/operation/service/FinancialExcelLayoutSupport.java`.
- Modify `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java`.
- Modify `backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java`.
- Modify `backend/src/main/java/com/church/operation/util/SystemAuditOperation.java`.
- Modify `backend/src/main/java/com/church/operation/service/SystemAuditService.java`.
- Modify `backend/pom.xml` and `backend/src/main/resources/application.yml` for build metadata/fallback version.

### Frontend

- Modify `frontend/src/api/churchInformation.ts`.
- Create `frontend/src/api/churchSettings.ts`.
- Create `frontend/src/stores/churchInformationStore.ts`.
- Modify `frontend/src/layouts/AppLayout.vue`.
- Modify `frontend/src/views/DashboardView.vue`.
- Modify `frontend/src/views/SystemAdministrationView.vue`.
- Modify `frontend/src/styles/main.css`.

### Documentation

- Modify English and Korean user-guide sources/builders and regenerate DOCX/PDF artifacts.
- Modify `docs/design-docs/backend features.md` and `docs/design-docs/frontend functionalities.md`.

---

### Task 1: Persist And Resolve Runtime Church Information

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/ChurchSettings.java`
- Create: `backend/src/main/java/com/church/operation/repo/ChurchSettingsRepository.java`
- Create: `backend/src/main/java/com/church/operation/service/EffectiveChurchInformation.java`
- Create: `backend/src/main/java/com/church/operation/service/ChurchInformationResolver.java`
- Test: `backend/src/test/java/com/church/operation/service/ChurchInformationResolverTest.java`

**Interfaces:**
- Consumes: `ChurchInformationProperties`, `ChurchSettingsRepository`.
- Produces: `EffectiveChurchInformation resolve()` and `Optional<ChurchSettings> savedSettings()`.
- `EffectiveChurchInformation` fields: `name`, `address`, `contactInfo`, `treasurerName`, `charityRegistrationNumber`, `receiptIssueLocation`, `website`, `logoUrl`, `bannerUrl`, `updatedAt`.

- [ ] **Step 1: Write resolver tests for defaults, complete overrides, and field-level fallback**

```java
@Test
void databaseValuesOverrideDefaultsAndBlankLegacyValuesFallBack() {
    ChurchSettings saved = new ChurchSettings();
    saved.setName("Runtime Church");
    saved.setAddress(" ");
    saved.setUpdatedAt(Instant.parse("2026-07-26T16:00:00Z"));
    when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));

    EffectiveChurchInformation result = resolver.resolve();

    assertThat(result.name()).isEqualTo("Runtime Church");
    assertThat(result.address()).isEqualTo(properties.information().address());
    assertThat(result.updatedAt()).isEqualTo(saved.getUpdatedAt());
}
```

- [ ] **Step 2: Run the resolver test and verify RED**

Run: `cd backend && mvn -Dtest=ChurchInformationResolverTest test`

Expected: FAIL because the entity, repository, effective record, and resolver do not exist.

- [ ] **Step 3: Implement the singleton entity and repository**

```java
@Document("church_settings")
public class ChurchSettings {
    public static final String SINGLETON_ID = "church-settings";
    @Id private String id = SINGLETON_ID;
    private String name;
    private String address;
    private String contactInfo;
    private String treasurerName;
    private String charityRegistrationNumber;
    private String receiptIssueLocation;
    private String website;
    private String logoGridFsId;
    private String logoContentType;
    private String bannerGridFsId;
    private String bannerContentType;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByMemberId;
    private String updatedByMemberId;
    // Conventional getters and setters following existing entities.
}
```

```java
public interface ChurchSettingsRepository extends MongoRepository<ChurchSettings, String> {
}
```

- [ ] **Step 4: Implement effective field resolution and versioned branding URLs**

```java
public record EffectiveChurchInformation(
    String name, String address, String contactInfo, String treasurerName,
    String charityRegistrationNumber, String receiptIssueLocation, String website,
    String logoUrl, String bannerUrl, Instant updatedAt
) {}
```

`ChurchInformationResolver.resolve()` must use trimmed nonblank database values, default each missing field from `ChurchInformationProperties`, and return versioned URLs such as `/api/church-information/logo?v=1785081600000` or `/banner?v=1785081600000` only when the corresponding GridFS identifier exists. Otherwise it returns `properties.branding().logPath()` and `bannerPath()`.

- [ ] **Step 5: Run resolver tests and verify GREEN**

Run: `cd backend && mvn -Dtest=ChurchInformationResolverTest test`

Expected: PASS.

- [ ] **Step 6: Commit the domain/resolver task**

```bash
git add backend/src/main/java/com/church/operation/entity/ChurchSettings.java backend/src/main/java/com/church/operation/repo/ChurchSettingsRepository.java backend/src/main/java/com/church/operation/service/EffectiveChurchInformation.java backend/src/main/java/com/church/operation/service/ChurchInformationResolver.java backend/src/test/java/com/church/operation/service/ChurchInformationResolverTest.java
git commit -m "feat: resolve runtime church information"
```

---

### Task 2: Validate And Store Branding In GridFS

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/ChurchBrandingService.java`
- Create: `backend/src/main/java/com/church/operation/exception/ChurchBrandingValidationException.java`
- Test: `backend/src/test/java/com/church/operation/service/ChurchBrandingServiceTest.java`

**Interfaces:**
- Consumes: `GridFSBucket`, `ChurchInformationProperties`, `MultipartFile`.
- Produces: `StoredBranding store(MultipartFile file, String kind)`, `Optional<BrandingContent> databaseFile(String id)`, `byte[] effectiveLogoBytes(ChurchSettings settings)`, `void deleteQuietly(String id)`.
- Records: `StoredBranding(String id, String contentType)`, `BrandingContent(byte[] bytes, String contentType)`.

- [ ] **Step 1: Write tests for PNG/JPEG validation and GridFS lifecycle**

```java
@Test
void acceptsDecodedPngAndRejectsSpoofedOrOversizedImages() {
    StoredBranding stored = service.store(file("logo.png", "image/png", validPng()), "logo");
    assertThat(stored.contentType()).isEqualTo("image/png");
    verify(bucket).uploadFromStream(startsWith("church-logo-"), any(InputStream.class), any());

    assertThatThrownBy(() -> service.store(
        file("fake.png", "image/png", "not-an-image".getBytes(UTF_8)), "logo"))
        .isInstanceOf(ChurchBrandingValidationException.class);
}
```

Add cases for 5 MB maximum, 8,000-pixel width/height, 40-megapixel area, missing GridFS file, bundled-resource fallback, and `deleteQuietly`.

- [ ] **Step 2: Run the branding test and verify RED**

Run: `cd backend && mvn -Dtest=ChurchBrandingServiceTest test`

Expected: FAIL because the service and validation exception do not exist.

- [ ] **Step 3: Implement decoded-image validation before upload**

```java
private ValidatedImage validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
        throw new ChurchBrandingValidationException("Choose a PNG or JPEG image no larger than 5 MB.");
    }
    byte[] bytes = file.getBytes();
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    String contentType = detectPngOrJpeg(bytes);
    if (image == null || contentType == null || image.getWidth() > 8000 || image.getHeight() > 8000
        || (long) image.getWidth() * image.getHeight() > 40_000_000L) {
        throw new ChurchBrandingValidationException("The image format or dimensions are not supported.");
    }
    return new ValidatedImage(bytes, contentType);
}
```

- [ ] **Step 4: Implement upload, retrieval, fallback resource loading, and idempotent deletion**

Use `GridFSUploadOptions.metadata(new Document("kind", kind).append("contentType", contentType))`, store `ObjectId.toHexString()`, download through `GridFSBucket.openDownloadStream(new ObjectId(id))`, and return `Optional.empty()` for invalid/missing IDs. Never include bytes in logs or exception messages.

- [ ] **Step 5: Run branding tests and verify GREEN**

Run: `cd backend && mvn -Dtest=ChurchBrandingServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit the branding storage task**

```bash
git add backend/src/main/java/com/church/operation/service/ChurchBrandingService.java backend/src/main/java/com/church/operation/exception/ChurchBrandingValidationException.java backend/src/test/java/com/church/operation/service/ChurchBrandingServiceTest.java
git commit -m "feat: store church branding in GridFS"
```

---

### Task 3: Add ADMIN Church Settings Workflow And Audit

**Files:**
- Create: `backend/src/main/java/com/church/operation/dto/ChurchSettingsSaveRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/ChurchSettingsAdminResponse.java`
- Create: `backend/src/main/java/com/church/operation/service/ChurchSettingsService.java`
- Modify: `backend/src/main/java/com/church/operation/util/SystemAuditOperation.java`
- Modify: `backend/src/main/java/com/church/operation/service/SystemAuditService.java`
- Test: `backend/src/test/java/com/church/operation/service/ChurchSettingsServiceTest.java`

**Interfaces:**
- Consumes: repository, resolver, branding service, audit service, ADMIN actor, optional logo/banner files.
- Produces: `ChurchSettingsAdminResponse get(Member actor)`, `ChurchSettingsAdminResponse save(Member actor, ChurchSettingsSaveRequest request, MultipartFile logo, MultipartFile banner)`, `ChurchSettingsAdminResponse reset(Member actor)`.

- [ ] **Step 1: Write failing service tests for authorization and transactional replacement order**

```java
@Test
void savesTextAndReplacesImagesWithoutDeletingOldFilesEarly() {
    when(repository.findById(ChurchSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
    when(branding.store(logo, "logo")).thenReturn(new StoredBranding("new-logo", "image/png"));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.save(admin, request(), logo, null);

    InOrder order = inOrder(branding, repository);
    order.verify(branding).store(logo, "logo");
    order.verify(repository).save(any());
    order.verify(branding).deleteQuietly("old-logo");
}
```

Add tests for non-ADMIN rejection, failed save deleting only newly uploaded files, reset deleting the document before old images, field trimming/limits, required name/address, and HTTP/HTTPS website validation.

- [ ] **Step 2: Run service tests and verify RED**

Run: `cd backend && mvn -Dtest=ChurchSettingsServiceTest test`

Expected: FAIL because the DTOs, service, and audit operations do not exist.

- [ ] **Step 3: Implement validated DTOs and ADMIN guard**

```java
public record ChurchSettingsSaveRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @Size(max = 500) String contactInfo,
    @Size(max = 200) String treasurerName,
    @Size(max = 200) String charityRegistrationNumber,
    @Size(max = 200) String receiptIssueLocation,
    @Size(max = 500) String website
) {}
```

Validate the website URI scheme in the service. Follow `EmailSettingsService.requireAdmin` for service-level authorization.

- [ ] **Step 4: Implement atomic-by-order replacement and reset cleanup**

The service uploads optional new files, saves one updated document, then removes replaced old files. Catch persistence failures, delete newly uploaded unreferenced files, record a safe failure audit, and rethrow. Reset deletes the singleton document first, then removes its old GridFS files.

- [ ] **Step 5: Add `CHURCH_SETTINGS_UPDATE` and `CHURCH_SETTINGS_RESET` audit operations**

Allow only boolean-like metadata keys `logoChanged` and `bannerChanged` plus `configurationSource`. Do not audit submitted text or image identifiers.

- [ ] **Step 6: Run service and audit tests and verify GREEN**

Run: `cd backend && mvn -Dtest=ChurchSettingsServiceTest,SystemAuditServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit the ADMIN workflow**

```bash
git add backend/src/main/java/com/church/operation/dto/ChurchSettingsSaveRequest.java backend/src/main/java/com/church/operation/dto/ChurchSettingsAdminResponse.java backend/src/main/java/com/church/operation/service/ChurchSettingsService.java backend/src/main/java/com/church/operation/util/SystemAuditOperation.java backend/src/main/java/com/church/operation/service/SystemAuditService.java backend/src/test/java/com/church/operation/service/ChurchSettingsServiceTest.java backend/src/test/java/com/church/operation/service/SystemAuditServiceTest.java
git commit -m "feat: manage runtime church settings"
```

---

### Task 4: Expose Effective Information, Branding, Admin API, And Build Version

**Files:**
- Create: `backend/src/main/java/com/church/operation/service/ApplicationVersionProvider.java`
- Create: `backend/src/main/java/com/church/operation/rest/ChurchSettingsController.java`
- Modify: `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`
- Modify: `backend/src/main/java/com/church/operation/config/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/pom.xml`
- Test: `backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java`
- Test: `backend/src/test/java/com/church/operation/rest/ChurchSettingsControllerTest.java`
- Test: `backend/src/test/java/com/church/operation/service/ApplicationVersionProviderTest.java`

**Interfaces:**
- `GET /api/church-information` returns current fields plus `applicationVersion`.
- `GET /api/church-information/logo` and `/banner` return effective image bytes/content type.
- `GET /api/admin/church-settings` returns editable effective fields and `source`.
- `PUT /api/admin/church-settings` consumes multipart parts `settings`, optional `logo`, optional `banner`.
- `POST /api/admin/church-settings/reset` resets overrides.

- [ ] **Step 1: Write failing API and build-version tests**

```java
mockMvc.perform(get("/api/church-information"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.applicationVersion").value("1.0.0-SNAPSHOT"))
    .andExpect(jsonPath("$.logPath").value("/api/church-information/logo?v=1"));
```

```java
mockMvc.perform(multipart(HttpMethod.PUT, "/api/admin/church-settings")
        .file(new MockMultipartFile("settings", "", "application/json", objectMapper.writeValueAsBytes(request)))
        .file(new MockMultipartFile("logo", "logo.png", "image/png", png)))
    .andExpect(status().isOk());
```

Add tests for ADMIN actor propagation, non-ADMIN `403`, image content type/body, reset, and development build-version fallback.

- [ ] **Step 2: Run API tests and verify RED**

Run: `cd backend && mvn -Dtest=ChurchInformationControllerTest,ChurchSettingsControllerTest,ApplicationVersionProviderTest test`

Expected: FAIL because the endpoints and provider are absent.

- [ ] **Step 3: Generate build metadata and implement version fallback**

Add the Spring Boot Maven plugin `build-info` goal beside `repackage`. `ApplicationVersionProvider` consumes `ObjectProvider<BuildProperties>` and returns `buildProperties.getVersion()` or `${church.application-version:1.0.0-SNAPSHOT}`.

- [ ] **Step 4: Refactor the public controller to use the resolver**

Keep response names `bannerPath` and `logPath` for frontend compatibility. Add `applicationVersion`. Image endpoints return `ResponseEntity<byte[]>` with the resolved content type and `Cache-Control: public, max-age=3600` because versioned URLs invalidate stale files.

- [ ] **Step 5: Implement the multipart ADMIN controller**

Use `@RequestPart("settings") @Valid ChurchSettingsSaveRequest`, optional `@RequestPart MultipartFile logo`, optional banner, and `(Member) authentication.getPrincipal()` following `EmailSettingsController`.

- [ ] **Step 6: Run API tests and verify GREEN**

Run: `cd backend && mvn -Dtest=ChurchInformationControllerTest,ChurchSettingsControllerTest,ApplicationVersionProviderTest test`

Expected: PASS.

- [ ] **Step 7: Verify packaged build metadata**

Run: `cd backend && mvn clean -DskipTests package`

Expected: BUILD SUCCESS and `META-INF/build-info.properties` inside the packaged JAR.

- [ ] **Step 8: Commit API and version support**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/church/operation/service/ApplicationVersionProvider.java backend/src/main/java/com/church/operation/rest/ChurchInformationController.java backend/src/main/java/com/church/operation/rest/ChurchSettingsController.java backend/src/main/java/com/church/operation/config/SecurityConfig.java backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java backend/src/test/java/com/church/operation/rest/ChurchSettingsControllerTest.java backend/src/test/java/com/church/operation/service/ApplicationVersionProviderTest.java
git commit -m "feat: expose church settings and app version"
```

---

### Task 5: Use Effective Church Information And Logo In Reports

**Files:**
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptService.java`
- Modify: `backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java`
- Modify: `backend/src/main/java/com/church/operation/service/FinancialExcelLayoutSupport.java`
- Modify: `backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java`
- Modify: `backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java`
- Test: `backend/src/test/java/com/church/operation/service/TaxReceiptServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java`
- Test: `backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java`

**Interfaces:**
- Consumes: `ChurchInformationResolver.resolve()` and `ChurchBrandingService.effectiveLogoBytes(ChurchSettings)`.
- Produces: receipts snapshot current effective text; all newly generated report files use current effective logo bytes.

- [ ] **Step 1: Write failing tests with database-backed effective values/logo**

```java
when(churchResolver.resolve()).thenReturn(runtimeChurchInformation());
when(branding.effectiveLogoBytes(any())).thenReturn(runtimeLogoBytes);

byte[] pdf = service.render(receipt);
assertThat(extractImages(pdf)).anySatisfy(image -> assertThat(image.bytes()).isEqualTo(runtimeLogoBytes));
```

For Excel tests, inspect `workbook.getAllPictures().getFirst().getData()` and verify runtime bytes. For tax receipt issuance, assert the saved snapshot uses runtime church name/address/website/treasurer values.

- [ ] **Step 2: Run report tests and verify RED**

Run: `cd backend && mvn -Dtest=TaxReceiptServiceTest,TaxReceiptPdfServiceTest,QuarterlyFinancialExcelServiceTest,YearlyFinancialExcelServiceTest test`

Expected: FAIL because report services still read immutable properties/classpath branding.

- [ ] **Step 3: Replace direct properties reads with resolver data**

Inject `ChurchInformationResolver` into `TaxReceiptService`. Preserve receipt snapshot semantics: values are copied when the official receipt is issued or replaced.

- [ ] **Step 4: Pass effective logo bytes into PDFBox and POI renderers**

Change `TaxReceiptPdfService.loadLogo` to consume `ChurchBrandingService`. Change `FinancialExcelLayoutSupport.addLogo` to accept `byte[] logoBytes`, detect PNG/JPEG from magic bytes, and keep existing dimensions/anchors. Quarterly/yearly services request bytes once per workbook.

- [ ] **Step 5: Run report tests and verify GREEN**

Run: `cd backend && mvn -Dtest=TaxReceiptServiceTest,TaxReceiptPdfServiceTest,QuarterlyFinancialExcelServiceTest,YearlyFinancialExcelServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit report integration**

```bash
git add backend/src/main/java/com/church/operation/service/TaxReceiptService.java backend/src/main/java/com/church/operation/service/TaxReceiptPdfService.java backend/src/main/java/com/church/operation/service/FinancialExcelLayoutSupport.java backend/src/main/java/com/church/operation/service/QuarterlyFinancialExcelService.java backend/src/main/java/com/church/operation/service/YearlyFinancialExcelService.java backend/src/test/java/com/church/operation/service/TaxReceiptServiceTest.java backend/src/test/java/com/church/operation/service/TaxReceiptPdfServiceTest.java backend/src/test/java/com/church/operation/service/QuarterlyFinancialExcelServiceTest.java backend/src/test/java/com/church/operation/service/YearlyFinancialExcelServiceTest.java
git commit -m "feat: use runtime branding in reports"
```

---

### Task 6: Verify Backup/Restore And Fiscal Preservation

**Files:**
- Modify: `backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java`
- Modify: `backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java`

**Interfaces:**
- Consumes: existing raw collection export/import, including `church_settings`, `fs.files`, and `fs.chunks`.
- Produces: regression proof that full restore retains branding and fiscal clean leaves it untouched.

- [ ] **Step 1: Extend the full round-trip fixture with church settings and two GridFS files**

```java
ObjectId logoId = uploadGridFs(database, "church-logo.png", logoBytes);
database.getCollection("church_settings").insertOne(new Document("_id", "church-settings")
    .append("name", "Runtime Church")
    .append("logoGridFsId", logoId.toHexString()));
```

After restore, assert the settings document matches and the GridFS download equals the original bytes.

- [ ] **Step 2: Extend fiscal round-trip assertions**

Seed `church_settings`, `fs.files`, and `fs.chunks`; run archive/clean; assert all remain byte-for-byte present.

- [ ] **Step 3: Run Docker-backed integration tests**

Run: `cd backend && mvn -Dtest=MongoDatabaseRoundTripIntegrationTest,FiscalArchiveRoundTripIntegrationTest test`

Expected: PASS when Docker is available. If Docker is unavailable, report the environment limitation and do not claim these tests passed.

- [ ] **Step 4: Commit persistence regressions**

```bash
git add backend/src/test/java/com/church/operation/service/MongoDatabaseRoundTripIntegrationTest.java backend/src/test/java/com/church/operation/service/FiscalArchiveRoundTripIntegrationTest.java
git commit -m "test: preserve church branding across data operations"
```

---

### Task 7: Add Shared Frontend Church Information State And Version Display

**Files:**
- Modify: `frontend/src/api/churchInformation.ts`
- Create: `frontend/src/stores/churchInformationStore.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/views/DashboardView.vue`
- Test: `frontend/src/stores/churchInformationStore.test.ts`
- Test: `frontend/src/layouts/AppLayout.test.ts`
- Test: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- `ChurchInformation` gains `applicationVersion: string`.
- Store exports `churchInformationState`, `loadChurchInformation(force?: boolean)`, `applyChurchInformation(value)`, and `resetChurchInformationStore()` for logout/tests.

- [ ] **Step 1: Write failing reactive-store and menu-version tests**

```ts
it('applies saved church information without another API request', async () => {
  await loadChurchInformation();
  applyChurchInformation({ ...initial, name: 'Updated Church', applicationVersion: '1.0.0' });
  expect(churchInformationState.value?.name).toBe('Updated Church');
});
```

Add AppLayout assertions for the version text directly below `Church Operations` and updated logo URL. Add Dashboard assertions that `applyChurchInformation` updates its banner/text.

- [ ] **Step 2: Run frontend tests and verify RED**

Run: `cd frontend && npm test -- --run src/stores/churchInformationStore.test.ts src/layouts/AppLayout.test.ts src/views/DashboardView.test.ts`

Expected: FAIL because the store/version field do not exist.

- [ ] **Step 3: Implement the shared reactive store**

```ts
export const churchInformationState = reactive<{
  value: ChurchInformation | null;
  loading: boolean;
}>({ value: null, loading: false });

export async function loadChurchInformation(force = false) {
  if (churchInformationState.value && !force) return churchInformationState.value;
  churchInformationState.loading = true;
  try {
    return applyChurchInformation(await getChurchInformation());
  } finally {
    churchInformationState.loading = false;
  }
}
```

- [ ] **Step 4: Refactor AppLayout and DashboardView to consume the store**

Keep graceful text-only fallback. Render `<small class="app-version">v{{ applicationVersion }}</small>` under the product title. Do not duplicate API fetching once the store is loaded.

- [ ] **Step 5: Run store/layout/dashboard tests and verify GREEN**

Run: `cd frontend && npm test -- --run src/stores/churchInformationStore.test.ts src/layouts/AppLayout.test.ts src/views/DashboardView.test.ts`

Expected: PASS.

- [ ] **Step 6: Commit shared frontend state**

```bash
git add frontend/src/api/churchInformation.ts frontend/src/stores/churchInformationStore.ts frontend/src/stores/churchInformationStore.test.ts frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.test.ts frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.test.ts
git commit -m "feat: refresh shared church branding"
```

---

### Task 8: Add The ADMIN Church Settings UI

**Files:**
- Create: `frontend/src/api/churchSettings.ts`
- Modify: `frontend/src/views/SystemAdministrationView.vue`
- Modify: `frontend/src/views/SystemAdministrationView.test.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- API exports `getChurchSettings()`, `saveChurchSettings(settings, logo?, banner?)`, and `resetChurchSettings()`.
- Save uses multipart `FormData` with JSON part name `settings` and optional file parts `logo`, `banner`.
- On save/reset, call `applyChurchInformation(response.effective)`.

- [ ] **Step 1: Write failing ADMIN UI tests**

```ts
it('uploads church settings and refreshes shared branding immediately', async () => {
  await fireEvent.click(screen.getByRole('tab', { name: 'Church Settings' }));
  await fireEvent.update(screen.getByLabelText('Church name'), 'Updated Church');
  await fireEvent.change(screen.getByLabelText('Church logo'), { target: { files: [pngFile] } });
  await fireEvent.click(screen.getByRole('button', { name: 'Save settings' }));

  expect(saveChurchSettingsMock).toHaveBeenCalledWith(
    expect.objectContaining({ name: 'Updated Church' }), pngFile, undefined,
  );
  expect(churchInformationState.value?.name).toBe('Updated Church');
});
```

Add tests for form loading, image preview, client type/size rejection, backend failure preserving the displayed effective state, reset confirmation, and source status.

- [ ] **Step 2: Run the view test and verify RED**

Run: `cd frontend && npm test -- --run src/views/SystemAdministrationView.test.ts`

Expected: FAIL because the tab, API, and form are absent.

- [ ] **Step 3: Implement the typed multipart API**

Use `authorizedFetch` from the existing client. Create a JSON `Blob` with content type `application/json`, append it as `settings`, append selected files only when present, and let the browser set the multipart boundary.

- [ ] **Step 4: Add the Church Settings tab and form**

Use Lucide `Church`, `ImageUp`, `Save`, and `RotateCcw` icons. Keep compact administration-page patterns, current-image previews, accessible labels, busy states, one confirmation dialog, and the in-page explanation required by the spec. Do not nest cards.

- [ ] **Step 5: Apply successful responses to shared state**

After save/reset, call `applyChurchInformation(response.effective)`, clear selected files/object URLs, reload editable status, and show success. Revoke preview object URLs on replacement and unmount.

- [ ] **Step 6: Run the view test and full frontend verification**

Run: `cd frontend && npm test -- --run src/views/SystemAdministrationView.test.ts`

Expected: PASS.

Run: `cd frontend && npm test -- --run && npm run build`

Expected: all tests PASS and production build succeeds.

- [ ] **Step 7: Commit the ADMIN UI**

```bash
git add frontend/src/api/churchSettings.ts frontend/src/views/SystemAdministrationView.vue frontend/src/views/SystemAdministrationView.test.ts frontend/src/styles/main.css
git commit -m "feat: add church settings administration"
```

---

### Task 9: Update Manuals And Feature Inventories

**Files:**
- Modify: `docs/user-guide/user-guide-content.md`
- Modify: `docs/user-guide/user-guide-content-ko.md`
- Modify: `docs/user-guide/build_user_guide.py`
- Modify: `docs/user-guide/build_user_guide_ko.py`
- Modify: `docs/user-guide/build_user_guide_ko_pdf.py`
- Regenerate: English/Korean DOCX and PDF manuals.
- Modify: `docs/design-docs/backend features.md`
- Modify: `docs/design-docs/frontend functionalities.md`

**Interfaces:**
- Documents the ADMIN-only fields, PNG/JPEG 5 MB limits, immediate refresh, reset defaults, full-backup behavior, and displayed application version.

- [ ] **Step 1: Update English and Korean guide sources**

Add one System Administration subsection describing Church Settings in the same sequence and terminology as the UI. Explicitly state that a relogin is not required and that full backup includes uploaded branding.

- [ ] **Step 2: Update both guide builders and regenerate artifacts**

Run the existing English and Korean guide build scripts with the bundled workspace Python. Keep current filenames stable.

- [ ] **Step 3: Render and visually inspect both DOCX manuals and PDFs**

Use the documents/PDF render workflow. Inspect every page for clipping, missing Hangul glyphs, broken screenshots, and section/page-number continuity.

- [ ] **Step 4: Update backend/frontend inventories**

Record the new entity, GridFS service, APIs, shared store, ADMIN tab, immediate refresh, and version display.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/user-guide docs/design-docs/backend\ features.md docs/design-docs/frontend\ functionalities.md
git commit -m "docs: document runtime church settings"
```

---

### Task 10: Final Verification And Review

**Files:**
- Verify all files changed by Tasks 1-9.

**Interfaces:**
- Produces a releasable church-settings feature before the MongoDB engine migration plan begins.

- [ ] **Step 1: Run backend tests without Docker exclusions**

Run: `cd backend && mvn test`

Expected: all tests PASS when Docker is available. If the only errors are unavailable Docker/Testcontainers, run the complete non-Docker suite and report the exact skipped integration classes.

- [ ] **Step 2: Run frontend tests and build**

Run: `cd frontend && npm test -- --run`

Expected: all tests PASS.

Run: `cd frontend && npm run build`

Expected: build succeeds.

- [ ] **Step 3: Run clean backend package and Compose validation**

Run: `cd backend && mvn clean -DskipTests package`

Expected: BUILD SUCCESS.

Run: `docker compose config --quiet`

Expected: exit 0.

- [ ] **Step 4: Rebuild the application and perform smoke tests**

Run: `docker compose up --build -d`

Verify backend startup, ADMIN Church Settings load/save/reset, immediate menu/dashboard refresh, logo in one PDF and one Excel report, version text, and full backup containing `church_settings`, `fs.files`, and `fs.chunks`.

- [ ] **Step 5: Check diffs and secrets**

Run: `git diff --check`

Expected: no source-text whitespace errors. Confirm no image bytes, credentials, or environment secrets were committed.

- [ ] **Step 6: Request code review and resolve findings**

Use `superpowers:requesting-code-review` against the implementation range. Fix Critical and Important findings, rerun affected verification, and document any remaining environment-only limitation.

- [ ] **Step 7: Commit final verification fixes if needed**

Run `git diff --name-only`, review every listed path, and stage only files changed to resolve review findings. Commit those paths with message `fix: address church settings review`. Skip this step when review produced no code changes.
