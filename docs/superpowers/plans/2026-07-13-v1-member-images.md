# v1.0 Member Face Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store protected member face images in MongoDB GridFS and display manageable previews in Members and My Profile.

**Architecture:** `MemberImageService` owns GridFS validation, authorization, replacement, and cleanup. Controllers expose authenticated manager and self-service binary endpoints; Vue fetches Blobs and revokes object URLs.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data MongoDB GridFS, byte-signature validation, Vue 3, JUnit 5, Mockito, Vitest, Vue Testing Library.

## Global Constraints

- Store JPEG, PNG, and WebP in MongoDB GridFS; reject other formats.
- Maximum size is exactly 5 MB.
- Admin/Membership manage any image; Member manages only their own.
- Show 38px list thumbnails and 96px form/profile previews.
- Use initials when no image exists.
- Full backup later must include GridFS; fiscal archive does not.

---

### Task 1: Add GridFS Image Storage And Validation

**Files:**
- Create: `backend/src/main/java/com/church/operation/config/MemberImageProperties.java`
- Create: `backend/src/main/java/com/church/operation/dto/MemberImageContent.java`
- Create: `backend/src/main/java/com/church/operation/service/MemberImageService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/church/operation/service/MemberImageServiceTest.java`

**Interfaces:**
- Produces: `store(Member actor, String memberId, MultipartFile file): String`, `load(Member actor, String memberId): MemberImageContent`, `remove(Member actor, String memberId): void`.

- [ ] **Step 1: Write failing validation and authorization tests**

```java
@Test
void storesValidPngAndReturnsGridFsId() {
    var file = multipart("face.png", "image/png", pngBytes());
    when(gridFsTemplate.store(any(InputStream.class), eq("face.png"), eq("image/png"), any(Document.class)))
        .thenReturn(new ObjectId("64b000000000000000000001"));
    assertThat(service.store(admin(), member().getId(), file))
        .isEqualTo("64b000000000000000000001");
}

@Test
void rejectsFileLargerThanFiveMegabytes() {
    assertThatThrownBy(() -> service.store(admin(), member().getId(),
        multipart("face.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Member image must not exceed 5 MB");
}

@Test
void memberCannotReplaceAnotherMembersImage() {
    assertThatThrownBy(() -> service.store(member(), "another-member", validPng()))
        .isInstanceOf(AccessDeniedException.class);
}
```

Add `rejectsDeclaredJpegWithInvalidSignature`, asserting `IllegalArgumentException("Image content does not match image/jpeg")`, and `replacementDeletesOldFileOnlyAfterMemberSave`, using Mockito `InOrder` to verify `memberRepository.save(member)` precedes `gridFsTemplate.delete(oldFileQuery)`. Define `pngBytes()` with the eight-byte PNG signature plus payload, `jpegBytes()` with `FF D8 FF`, and `webpBytes()` with `RIFF`, a little-endian length, `WEBP`, and a `VP8 `/`VP8L`/`VP8X` chunk marker.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=MemberImageServiceTest test`

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Add configuration**

```yaml
church:
  member-image:
    max-size: ${CHURCH_MEMBER_IMAGE_MAX_SIZE:5MB}
```

Bind as `DataSize maxSize` and validate it is positive.

- [ ] **Step 4: Implement content validation and GridFS operations**

Use `GridFsTemplate.store`, `findOne`, `getResource`, and `delete`. Detect content without trusting file names: PNG requires `89 50 4E 47 0D 0A 1A 0A`, JPEG requires `FF D8 FF`, and WebP requires `RIFF` at bytes 0-3, `WEBP` at bytes 8-11, and `VP8 `, `VP8L`, or `VP8X` at bytes 12-15. Compare the detected type with the declared `Content-Type`. Store metadata:

```java
Document metadata = new Document()
    .append("memberId", memberId)
    .append("uploadedBy", actor.getId())
    .append("uploadedAt", Instant.now())
    .append("contentType", detectedType);
```

On replacement: store new file, save member reference, then delete old file. If member save fails, delete new file.

- [ ] **Step 5: Run service tests**

Run: `cd backend && mvn -Dtest=MemberImageServiceTest test`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/church/operation/config/MemberImageProperties.java backend/src/main/java/com/church/operation/dto/MemberImageContent.java backend/src/main/java/com/church/operation/service/MemberImageService.java backend/src/main/resources/application.yml backend/src/test/java/com/church/operation/service/MemberImageServiceTest.java
git commit -m "feat: store member images in GridFS"
```

### Task 2: Add Authenticated Image Endpoints

**Files:**
- Modify: `backend/src/main/java/com/church/operation/rest/MemberController.java`
- Create: `backend/src/test/java/com/church/operation/rest/MemberControllerTest.java`
- Modify: `backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `MemberImageService` from Task 1.
- Produces: GET/PUT/DELETE manager and `/me` image endpoints.

- [ ] **Step 1: Write failing MockMvc tests**

Verify:

```java
mockMvc.perform(multipart("/api/members/member-1/image")
        .file(validPng).with(request -> { request.setMethod("PUT"); return request; })
        .with(authentication(adminAuthentication())))
    .andExpect(status().isOk());
```

Also test self-service, forbidden cross-member access, image content type/cache headers, not found, and delete.

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=MemberControllerTest test`

Expected: endpoint tests return 404.

- [ ] **Step 3: Implement explicit endpoints**

```java
@GetMapping("/{id}/image")
ResponseEntity<byte[]> image(Authentication auth, @PathVariable("id") String id) {
    MemberImageContent content = memberImageService.load(actor(auth), id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(content.bytes());
}

@PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
MemberResponse replaceImage(Authentication auth, @PathVariable("id") String id,
    @RequestPart("file") MultipartFile file) {
    memberImageService.store(actor(auth), id, file);
    return MemberResponse.from(memberService.getMember(actor(auth), id));
}

@DeleteMapping("/{id}/image")
ResponseEntity<Void> removeImage(Authentication auth, @PathVariable("id") String id) {
    memberImageService.remove(actor(auth), id);
    return ResponseEntity.noContent().build();
}
```

Add `GET /me/image`, `PUT /me/image`, and `DELETE /me/image` methods that pass `actor.getId()` to the same service. Return `Cache-Control: private, max-age=300` and the detected media type.

- [ ] **Step 4: Run controller and service tests**

Run: `cd backend && mvn -Dtest=MemberControllerTest,MemberImageServiceTest test`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/church/operation/rest/MemberController.java backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java backend/src/test/java/com/church/operation/rest/MemberControllerTest.java
git commit -m "feat: expose protected member image endpoints"
```

### Task 3: Add Blob-Capable Frontend HTTP Helpers

**Files:**
- Modify: `frontend/src/api/http.ts`
- Modify: `frontend/src/api/members.ts`
- Create: `frontend/src/api/http.test.ts`

**Interfaces:**
- Produces: `getBlob(path): Promise<Blob>`, `putFile<T>(path, field, file): Promise<T>`, image API helpers.

- [ ] **Step 1: Write failing HTTP helper tests**

Assert that JSON requests retain `Content-Type: application/json`, multipart requests do not manually set a multipart boundary, and Blob requests carry Authorization.

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm test -- http.test.ts`

Expected: missing helper failures.

- [ ] **Step 3: Refactor request headers safely**

```ts
if (!(init.body instanceof FormData)) {
  headers.set('Content-Type', 'application/json');
}
```

Export:

```ts
export async function getBlob(path: string): Promise<Blob>;
export async function putFile<T>(path: string, field: string, file: File): Promise<T>;
export async function deleteEmpty(path: string): Promise<void>;
```

- [ ] **Step 4: Add member image API functions**

```ts
export const getMemberImage = (id: string) => getBlob(`/api/members/${id}/image`);
export const replaceMemberImage = (id: string, file: File) => putFile<Member>(`/api/members/${id}/image`, 'file', file);
export const removeMemberImage = (id: string) => deleteEmpty(`/api/members/${id}/image`);
```

Add `getSelfImage()`, `replaceSelfImage(file)`, and `removeSelfImage()` using `/api/members/me/image`.

- [ ] **Step 5: Run tests and commit**

Run: `cd frontend && npm test -- http.test.ts`

Expected: pass.

```bash
git add frontend/src/api/http.ts frontend/src/api/http.test.ts frontend/src/api/members.ts
git commit -m "feat: add authenticated file API helpers"
```

### Task 4: Add Member And Profile Image UI

**Files:**
- Modify: `frontend/src/views/MembersView.vue`
- Modify: `frontend/src/views/ProfileView.vue`
- Modify: `frontend/src/styles/main.css`
- Create: `frontend/src/components/MemberAvatar.vue`
- Create: `frontend/src/components/MemberImageEditor.vue`
- Test: `frontend/src/components/MemberImageEditor.test.ts`
- Create: `frontend/src/views/MembersView.test.ts`
- Create: `frontend/src/views/ProfileView.test.ts`

**Interfaces:**
- Consumes: image API helpers from Task 3.
- Produces: reusable initials/image avatar and image editor.

- [ ] **Step 1: Write failing component tests**

Test initials fallback, 5 MB client rejection, accepted PNG upload, replace/remove calls, manager member ID, and self-service endpoint selection.

- [ ] **Step 2: Run and verify failure**

Run: `cd frontend && npm test -- MemberImageEditor.test.ts MembersView.test.ts ProfileView.test.ts`

Expected: component import failures.

- [ ] **Step 3: Implement object URL lifecycle**

```ts
watch(() => props.memberId, async () => {
  revokeCurrentUrl();
  try { imageUrl.value = URL.createObjectURL(await props.load()); }
  catch { imageUrl.value = ''; }
}, { immediate: true });

onBeforeUnmount(revokeCurrentUrl);
```

Use a hidden file input triggered by a clear command button. Keep stable 38px and 96px dimensions.

- [ ] **Step 4: Integrate approved placements**

- Members table first column: 38px avatar plus name/nickname.
- Edit/create form top: 96px preview and Replace/Remove.
- My Profile: same 96px component in self-service mode.

- [ ] **Step 5: Run tests and build**

Run: `cd frontend && npm test -- MemberImageEditor.test.ts MembersView.test.ts ProfileView.test.ts`

Expected: pass.

Run: `cd frontend && npm run build`

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/MembersView.vue frontend/src/views/ProfileView.vue frontend/src/styles/main.css frontend/src/components/MemberAvatar.vue frontend/src/components/MemberImageEditor.vue frontend/src/components/MemberImageEditor.test.ts frontend/src/views/MembersView.test.ts frontend/src/views/ProfileView.test.ts
git commit -m "feat: add member face image controls"
```

### Task 5: Verify Member Image Slice

**Files:**
- Modify only for scoped verification fixes.

**Interfaces:**
- Produces: verified GridFS feature for full-backup plan.

- [ ] **Step 1: Run complete suites**

Run: `cd backend && mvn test`

Run: `cd frontend && npm test && npm run build`

Expected: all pass.

- [ ] **Step 2: Docker smoke test**

Upload, reload, replace, and remove an image as Membership. Repeat self-service as Member. Confirm another Member cannot access a different member's protected image.

- [ ] **Step 3: Verify MongoDB GridFS state**

Confirm one current `fs.files` record per imaged member and no orphan after replacement/removal.

- [ ] **Step 4: Capture responsive screenshots and report any defect back to its owning task**

Verify Members and My Profile at desktop/mobile with no shifting columns or clipped controls. End with `git diff --check`; expected output is empty.
