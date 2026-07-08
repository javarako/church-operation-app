# Church Operations Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the locally runnable foundation for the church operations app: Docker Compose, Spring Boot backend, MongoDB connection, Vue app shell, seeded admin login, forced first password change, member-backed user model, church information config, and role-aware navigation.

**Architecture:** Use a modular monolith backend with package boundaries for `config`, `dto`, `entity`, `exception`, `filter`, `repo`, `rest`, `service`, and `util`. Use a separate Vue 3 frontend that talks to backend REST APIs. Docker Compose runs MongoDB, backend, and frontend locally.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data MongoDB, MongoDB 6+, Vue 3.x, Vue Router, Vitest, Vue Testing Library, JUnit 5, Spring Test, Mockito, Docker Compose.

## Global Constraints

- Backend language/version: Java 21.
- Frontend framework: Vue 3.x.
- Backend framework: Spring Boot 4.
- Security: Spring Security.
- Storage: MongoDB 6+.
- Frontend routing: Vue Router.
- Local orchestration: Docker Compose starts MongoDB, backend, and frontend together.
- Seeded bootstrap login: username `admin`, temporary password `password`, role `ADMIN`, forced password change on first login.
- Normal member-backed login ID: mandatory unique `primaryEmail`.
- Roles: `ADMIN`, `TREASURER`, `PASTOR`, `MEMBERSHIP`, `VIEWER`, `MEMBER`.
- Church branding assets: banner image and church icon are served from backend resources.
- Church information config: name, address, contact info, and treasurer name come from `application.yml`.

---

## File Structure

Create a repository layout with clear application boundaries:

```text
.
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/church/operation/
│       │   │   ├── ChurchOperationApplication.java
│       │   │   ├── dto/
│       │   │   ├── entity/
│       │   │   ├── exception/
│       │   │   ├── filter/
│       │   │   ├── config/
│       │   │   ├── repo/
│       │   │   ├── rest/
│       │   │   ├── service/
│       │   │   └── util/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── static/branding/
│       │           ├── banner-placeholder.txt
│       │           └── icon-placeholder.txt
│       └── test/java/com/church/operation/
├── frontend/
│   ├── Dockerfile
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.vue
│       ├── main.ts
│       ├── api/
│       ├── auth/
│       ├── layouts/
│       ├── router/
│       ├── styles/
│       └── views/
├── docker-compose.yml
└── docs/superpowers/
```

## Task 1: Scaffold Dockerized Project Foundation

**Files:**
- Create: `docker-compose.yml`
- Create: `backend/Dockerfile`
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/static/branding/banner-placeholder.txt`
- Create: `backend/src/main/resources/static/branding/icon-placeholder.txt`
- Create: `frontend/Dockerfile`
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`

**Interfaces:**
- Produces: Backend health endpoint base path `/actuator/health`.
- Produces: Frontend dev server on port `5173`.
- Produces: Backend server on port `8080`.
- Produces: MongoDB server on port `27017`, database `church_operations`.

- [ ] **Step 1: Create backend build file**

Create `backend/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.church</groupId>
  <artifactId>operation</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>church-operation-backend</name>

  <properties>
    <java.version>21</java.version>
    <spring-boot.version>4.0.0</spring-boot.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>${spring-boot.version}</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <release>21</release>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create Spring Boot entry point**

Create `backend/src/main/java/com/church/operation/ChurchOperationApplication.java`:

```java
package com.church.operation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChurchOperationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChurchOperationApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application configuration**

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: church-operation
  mongodb:
    uri: ${SPRING_MONGODB_URI:mongodb://localhost:27017/church_operations}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health

church:
  information:
    name: ${CHURCH_NAME:Church Name}
    address: ${CHURCH_ADDRESS:123 Church Street, Toronto, ON}
    contact-info: ${CHURCH_CONTACT_INFO:416-555-0100}
    treasurer-name: ${CHURCH_TREASURER_NAME:Treasurer}
  branding:
    banner-path: /branding/banner-placeholder.txt
    log-path: /branding/icon-placeholder.txt
```

- [ ] **Step 4: Create branding placeholders**

Create `backend/src/main/resources/static/branding/church-banner.png`:

```text
Replace this file with the church banner image.
```

Create `backend/src/main/resources/static/branding/church_logo.png`:

```text
Replace this file with the church logo image.
```

- [ ] **Step 5: Create backend Dockerfile**

Create `backend/Dockerfile`:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/operation-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Create frontend package**

Create `frontend/package.json`:

```json
{
  "name": "church-operation-frontend",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0",
    "build": "vue-tsc --noEmit && vite build",
    "test": "vitest run"
  },
  "dependencies": {
    "@vitejs/plugin-vue": "^5.2.0",
    "vue": "^3.5.0",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "@testing-library/vue": "^8.1.0",
    "@types/node": "^22.10.0",
    "jsdom": "^25.0.1",
    "typescript": "^5.7.0",
    "vite": "^6.0.0",
    "vitest": "^2.1.0",
    "vue-tsc": "^2.1.10"
  }
}
```

- [ ] **Step 7: Create frontend entry files**

Create `frontend/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Church Operations</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

Create `frontend/src/main.ts`:

```ts
import { createApp } from 'vue';
import App from './App.vue';

createApp(App).mount('#app');
```

Create `frontend/src/App.vue`:

```vue
<template>
  <main class="app-shell">
    <h1>Church Operations</h1>
  </main>
</template>

<style>
body {
  margin: 0;
  font-family: Arial, sans-serif;
  color: #1f2933;
  background: #f7f8fa;
}

.app-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
</style>
```

- [ ] **Step 8: Create frontend Dockerfile**

Create `frontend/Dockerfile`:

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev"]
```

- [ ] **Step 9: Create Docker Compose**

Create `docker-compose.yml`:

```yaml
services:
  mongo:
    image: mongo:6
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db

  backend:
    build:
      context: ./backend
    environment:
      SPRING_MONGODB_URI: mongodb://mongo:27017/church_operations
      CHURCH_ADDRESS: 123 Church Street, Toronto, ON
      CHURCH_CONTACT_INFO: 416-555-0100
      CHURCH_TREASURER_NAME: Treasurer
    ports:
      - "8080:8080"
    depends_on:
      - mongo

  frontend:
    build:
      context: ./frontend
    ports:
      - "5173:5173"
    depends_on:
      - backend

volumes:
  mongo-data:
```

- [ ] **Step 10: Verify foundation build**

Run: `docker compose build`

Expected: backend and frontend images build successfully.

Run: `docker compose up`

Expected: MongoDB, backend, and frontend start. Backend health is available at `http://localhost:8080/actuator/health`. Frontend is available at `http://localhost:5173`.

- [ ] **Step 11: Commit**

```bash
git add docker-compose.yml backend frontend
git commit -m "chore: scaffold dockerized church operations app"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Task 2: Add API Errors, Roles, And Church Information Config

**Files:**
- Create: `backend/src/main/java/com/church/operation/util/Role.java`
- Create: `backend/src/main/java/com/church/operation/dto/ApiError.java`
- Create: `backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java`
- Create: `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`
- Test: `backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java`

**Interfaces:**
- Produces: `enum Role { ADMIN, TREASURER, PASTOR, MEMBERSHIP, VIEWER, MEMBER }`
- Produces: `record ApiError(String code, String message)`
- Produces: `GET /api/church-information`
- Produces response:

```json
{
  "name": "Church Name",
  "address": "123 Church Street, Toronto, ON",
  "contactInfo": "416-555-0100",
  "treasurerName": "Treasurer",
  "bannerPath": "/branding/banner-placeholder.txt",
  "logPath": "/branding/icon-placeholder.txt"
}
```

- [ ] **Step 1: Write failing church information controller test**

Create `backend/src/test/java/com/church/operation/rest/ChurchInformationControllerTest.java`:

```java
package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChurchInformationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChurchInformationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsConfiguredChurchInformation() throws Exception {
        mockMvc.perform(get("/api/church-information"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Church Name"))
            .andExpect(jsonPath("$.address").value("123 Church Street, Toronto, ON"))
            .andExpect(jsonPath("$.contactInfo").value("416-555-0100"))
            .andExpect(jsonPath("$.treasurerName").value("Treasurer"))
            .andExpect(jsonPath("$.bannerPath").value("/branding/banner-placeholder.txt"))
            .andExpect(jsonPath("$.logPath").value("/branding/icon-placeholder.txt"));
    }

    @TestConfiguration
    @EnableConfigurationProperties(ChurchInformationProperties.class)
    static class TestConfig {
        @Bean
        ChurchInformationController churchInformationController(ChurchInformationProperties properties) {
            return new ChurchInformationController(properties);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=ChurchInformationControllerTest`

Expected: FAIL because `ChurchInformationController` and `ChurchInformationProperties` do not exist.

- [ ] **Step 3: Add role enum**

Create `backend/src/main/java/com/church/operation/util/Role.java`:

```java
package com.church.operation.util;

public enum Role {
    ADMIN,
    TREASURER,
    PASTOR,
    MEMBERSHIP,
    VIEWER,
    MEMBER
}
```

- [ ] **Step 4: Add API error contract**

Create `backend/src/main/java/com/church/operation/dto/ApiError.java`:

```java
package com.church.operation.dto;

public record ApiError(String code, String message) {
}
```

Create `backend/src/main/java/com/church/operation/exception/GlobalExceptionHandler.java`:

```java
package com.church.operation.exception;

import com.church.operation.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Request validation failed."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", ex.getMessage()));
    }
}
```

- [ ] **Step 5: Add church information config binding**

Create `backend/src/main/java/com/church/operation/config/ChurchInformationProperties.java`:

```java
package com.church.operation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "church")
public record ChurchInformationProperties(
    Information information,
    Branding branding
) {
    public record Information(String name, String address, String contactInfo, String treasurerName) {
    }

    public record Branding(String bannerPath, String logPath) {
    }
}
```

- [ ] **Step 6: Add church information API**

Create `backend/src/main/java/com/church/operation/rest/ChurchInformationController.java`:

```java
package com.church.operation.rest;

import com.church.operation.config.ChurchInformationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/church-information")
public class ChurchInformationController {
    private final ChurchInformationProperties properties;

    public ChurchInformationController(ChurchInformationProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    ChurchInformationResponse getChurchInformation() {
        return new ChurchInformationResponse(
            properties.information().name(),
            properties.information().address(),
            properties.information().contactInfo(),
            properties.information().treasurerName(),
            properties.branding().bannerPath(),
            properties.branding().logPath()
        );
    }

    record ChurchInformationResponse(
        String name,
        String address,
        String contactInfo,
        String treasurerName,
        String bannerPath,
        String logPath
    ) {
    }
}
```

Modify `ChurchOperationApplication.java`:

```java
package com.church.operation;

import com.church.operation.config.ChurchInformationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ChurchInformationProperties.class)
public class ChurchOperationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChurchOperationApplication.class, args);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ChurchInformationControllerTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/church/operation backend/src/test/java/com/church/operation
git commit -m "feat: expose church information configuration"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Task 3: Add Member Document, Repository, And Validation

**Files:**
- Create: `backend/src/main/java/com/church/operation/entity/Address.java`
- Create: `backend/src/main/java/com/church/operation/entity/Member.java`
- Create: `backend/src/main/java/com/church/operation/repo/MemberRepository.java`
- Create: `backend/src/main/java/com/church/operation/service/MemberService.java`
- Test: `backend/src/test/java/com/church/operation/service/MemberServiceTest.java`

**Interfaces:**
- Produces: `MemberService.createBootstrapAdminMember()`
- Produces: `MemberService.findByPrimaryEmail(String primaryEmail)`
- Consumes: `Role.ADMIN` from Task 2.

- [ ] **Step 1: Write failing member service tests**

Create `backend/src/test/java/com/church/operation/service/MemberServiceTest.java`:

```java
package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Test
    void createsBootstrapAdminMemberWhenMissing() {
        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.empty());
        when(memberRepository.save(org.mockito.ArgumentMatchers.any(Member.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MemberService service = new MemberService(memberRepository);

        Member member = service.createBootstrapAdminMember();

        assertThat(member.getPrimaryEmail()).isEqualTo("admin");
        assertThat(member.getRoles()).containsExactly(Role.ADMIN);
        assertThat(member.isMustChangePassword()).isTrue();
        verify(memberRepository).save(org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void rejectsBlankPrimaryEmail() {
        MemberService service = new MemberService(memberRepository);

        assertThatThrownBy(() -> service.validatePrimaryEmail(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Primary email is required.");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MemberServiceTest`

Expected: FAIL because member classes do not exist.

- [ ] **Step 3: Add address value object**

Create `backend/src/main/java/com/church/operation/entity/Address.java`:

```java
package com.church.operation.entity;

public record Address(
    String addressLine1,
    String addressLine2,
    String city,
    String provinceState,
    String postalZipCode,
    String country
) {
}
```

- [ ] **Step 4: Add member document**

Create `backend/src/main/java/com/church/operation/entity/Member.java`:

```java
package com.church.operation.entity;

import com.church.operation.util.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Document("members")
public class Member {
    @Id
    private String id;

    @Indexed(unique = true)
    private String primaryEmail;

    private String secondaryEmail;
    private String primaryPhone;
    private String secondaryPhone;
    private String mobilePhone;
    private Address mailingAddress;
    private String displayName;
    private String nickname;
    private LocalDate birthDate;
    private String groupCode;
    private String membershipStatus;

    @Indexed(unique = true, sparse = true)
    private String offeringNumber;

    private String faceImageAttachmentId;
    private String householdName;
    private String notes;
    private Set<Role> roles = new LinkedHashSet<>();
    private boolean active = true;
    private boolean locked = false;
    private boolean mustChangePassword = false;
    private String passwordHash;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }
    public String getSecondaryEmail() { return secondaryEmail; }
    public void setSecondaryEmail(String secondaryEmail) { this.secondaryEmail = secondaryEmail; }
    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }
    public String getSecondaryPhone() { return secondaryPhone; }
    public void setSecondaryPhone(String secondaryPhone) { this.secondaryPhone = secondaryPhone; }
    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
    public Address getMailingAddress() { return mailingAddress; }
    public void setMailingAddress(Address mailingAddress) { this.mailingAddress = mailingAddress; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(String membershipStatus) { this.membershipStatus = membershipStatus; }
    public String getOfferingNumber() { return offeringNumber; }
    public void setOfferingNumber(String offeringNumber) { this.offeringNumber = offeringNumber; }
    public String getFaceImageAttachmentId() { return faceImageAttachmentId; }
    public void setFaceImageAttachmentId(String faceImageAttachmentId) { this.faceImageAttachmentId = faceImageAttachmentId; }
    public String getHouseholdName() { return householdName; }
    public void setHouseholdName(String householdName) { this.householdName = householdName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
```

- [ ] **Step 5: Add repository**

Create `backend/src/main/java/com/church/operation/repo/MemberRepository.java`:

```java
package com.church.operation.repo;

import com.church.operation.entity.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {
    Optional<Member> findByPrimaryEmail(String primaryEmail);
    boolean existsByPrimaryEmail(String primaryEmail);
    boolean existsByOfferingNumber(String offeringNumber);
}
```

- [ ] **Step 6: Add service**

Create `backend/src/main/java/com/church/operation/service/MemberService.java`:

```java
package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member createBootstrapAdminMember() {
        Optional<Member> existing = memberRepository.findByPrimaryEmail("admin");
        if (existing.isPresent()) {
            return existing.get();
        }

        Member member = new Member();
        member.setPrimaryEmail("admin");
        member.setDisplayName("System Administrator");
        member.setRoles(Set.of(Role.ADMIN));
        member.setActive(true);
        member.setLocked(false);
        member.setMustChangePassword(true);
        return memberRepository.save(member);
    }

    public Optional<Member> findByPrimaryEmail(String primaryEmail) {
        validatePrimaryEmail(primaryEmail);
        return memberRepository.findByPrimaryEmail(primaryEmail.trim().toLowerCase());
    }

    public Member save(Member member) {
        validatePrimaryEmail(member.getPrimaryEmail());
        member.setPrimaryEmail(member.getPrimaryEmail().trim().toLowerCase());
        return memberRepository.save(member);
    }

    void validatePrimaryEmail(String primaryEmail) {
        if (primaryEmail == null || primaryEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary email is required.");
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=MemberServiceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/church/operation/entity backend/src/main/java/com/church/operation/repo backend/src/main/java/com/church/operation/service backend/src/test/java/com/church/operation/service
git commit -m "feat: add member document foundation"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Task 4: Add Bootstrap Admin And Authentication API

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/AuthController.java`
- Create: `backend/src/main/java/com/church/operation/service/AuthService.java`
- Create: `backend/src/main/java/com/church/operation/service/BootstrapAdminRunner.java`
- Create: `backend/src/main/java/com/church/operation/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/church/operation/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/church/operation/dto/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/church/operation/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/church/operation/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `MemberService.createBootstrapAdminMember()` from Task 3.
- Produces: `POST /api/auth/login`
- Produces: `POST /api/auth/change-password`
- Produces: login response with `primaryEmail`, `displayName`, `roles`, `mustChangePassword`.

- [ ] **Step 1: Write failing auth service tests**

Create `backend/src/test/java/com/church/operation/service/AuthServiceTest.java`:

```java
package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.dto.LoginRequest;
import com.church.operation.dto.LoginResponse;
import com.church.operation.repo.MemberRepository;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsForcedPasswordChangeForBootstrapAdmin() {
        Member admin = new Member();
        admin.setPrimaryEmail("admin");
        admin.setDisplayName("System Administrator");
        admin.setPasswordHash("hashed-password");
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setMustChangePassword(true);
        admin.setActive(true);

        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        AuthService service = new AuthService(memberRepository, passwordEncoder);

        LoginResponse response = service.login(new LoginRequest("admin", "password"));

        assertThat(response.primaryEmail()).isEqualTo("admin");
        assertThat(response.roles()).containsExactly(Role.ADMIN);
        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void loginRejectsInvalidPassword() {
        Member admin = new Member();
        admin.setPrimaryEmail("admin");
        admin.setPasswordHash("hashed-password");
        admin.setActive(true);

        when(memberRepository.findByPrimaryEmail("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("bad", "hashed-password")).thenReturn(false);

        AuthService service = new AuthService(memberRepository, passwordEncoder);

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "bad")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid username or password.");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AuthServiceTest`

Expected: FAIL because auth classes do not exist.

- [ ] **Step 3: Add auth DTOs**

Create `backend/src/main/java/com/church/operation/dto/LoginRequest.java`:

```java
package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {
}
```

Create `backend/src/main/java/com/church/operation/dto/LoginResponse.java`:

```java
package com.church.operation.dto;

import com.church.operation.util.Role;

import java.util.Set;

public record LoginResponse(
    String primaryEmail,
    String displayName,
    Set<Role> roles,
    boolean mustChangePassword
) {
}
```

Create `backend/src/main/java/com/church/operation/dto/ChangePasswordRequest.java`:

```java
package com.church.operation.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank String username,
    @NotBlank String currentPassword,
    @NotBlank String newPassword
) {
}
```

- [ ] **Step 4: Add auth service**

Create `backend/src/main/java/com/church/operation/service/AuthService.java`:

```java
package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.repo.MemberRepository;
import com.church.operation.dto.ChangePasswordRequest;
import com.church.operation.dto.LoginRequest;
import com.church.operation.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByPrimaryEmail(normalize(request.username()))
            .filter(Member::isActive)
            .filter(candidate -> !candidate.isLocked())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        return new LoginResponse(
            member.getPrimaryEmail(),
            member.getDisplayName(),
            member.getRoles(),
            member.isMustChangePassword()
        );
    }

    public void changePassword(ChangePasswordRequest request) {
        Member member = memberRepository.findByPrimaryEmail(normalize(request.username()))
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        if (request.newPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        member.setMustChangePassword(false);
        memberRepository.save(member);
    }

    private String normalize(String username) {
        return username.trim().toLowerCase();
    }
}
```

- [ ] **Step 5: Add security config**

Create `backend/src/main/java/com/church/operation/config/SecurityConfig.java`:

```java
package com.church.operation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/change-password", "/api/church-information", "/actuator/health", "/branding/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 6: Add auth controller**

Create `backend/src/main/java/com/church/operation/rest/AuthController.java`:

```java
package com.church.operation.rest;

import com.church.operation.dto.ChangePasswordRequest;
import com.church.operation.dto.LoginRequest;
import com.church.operation.dto.LoginResponse;
import com.church.operation.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/change-password")
    void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }
}
```

- [ ] **Step 7: Add bootstrap admin runner**

Create `backend/src/main/java/com/church/operation/service/BootstrapAdminRunner.java`:

```java
package com.church.operation.service;

import com.church.operation.entity.Member;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(MemberService memberService, PasswordEncoder passwordEncoder) {
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Member admin = memberService.createBootstrapAdminMember();
        if (admin.getPasswordHash() == null || admin.getPasswordHash().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode("password"));
            admin.setMustChangePassword(true);
            memberService.save(admin);
        }
    }
}
```

- [ ] **Step 8: Run auth tests**

Run: `cd backend && mvn test -Dtest=AuthServiceTest,MemberServiceTest`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/church/operation/dto backend/src/main/java/com/church/operation/rest backend/src/main/java/com/church/operation/service backend/src/main/java/com/church/operation/config/SecurityConfig.java backend/src/test/java/com/church/operation/service
git commit -m "feat: add bootstrap admin authentication"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Task 5: Add Vue Router, Auth Client, Login, Password Change, And Shell

**Files:**
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/auth/authStore.ts`
- Create: `frontend/src/auth/roles.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/layouts/AppLayout.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/ChangePasswordView.vue`
- Create: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Test: `frontend/src/auth/authStore.test.ts`
- Test: `frontend/src/router/router.test.ts`

**Interfaces:**
- Consumes: `POST /api/auth/login` from Task 4.
- Consumes: `POST /api/auth/change-password` from Task 4.
- Produces: role-aware frontend routes for dashboard, members, offerings, finance, budgets, reference data, reports, and profile.

- [ ] **Step 1: Write failing auth store test**

Create `frontend/src/auth/authStore.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { canAccessRoute, type Role } from './authStore';

describe('authStore', () => {
  it('allows treasurer to access finance routes', () => {
    expect(canAccessRoute(['TREASURER'], ['TREASURER', 'ADMIN'])).toBe(true);
  });

  it('blocks member from finance routes', () => {
    expect(canAccessRoute(['MEMBER'], ['TREASURER', 'ADMIN'])).toBe(false);
  });

  it('allows member self-service route', () => {
    const roles: Role[] = ['MEMBER'];
    expect(canAccessRoute(roles, ['MEMBER', 'ADMIN'])).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- authStore.test.ts`

Expected: FAIL because `authStore.ts` does not exist.

- [ ] **Step 3: Add auth store and role helper**

Create `frontend/src/auth/authStore.ts`:

```ts
export type Role = 'ADMIN' | 'TREASURER' | 'PASTOR' | 'MEMBERSHIP' | 'VIEWER' | 'MEMBER';

export interface CurrentUser {
  primaryEmail: string;
  displayName: string;
  roles: Role[];
  mustChangePassword: boolean;
}

export const authState: { currentUser: CurrentUser | null } = {
  currentUser: null,
};

export function setCurrentUser(user: CurrentUser | null) {
  authState.currentUser = user;
}

export function canAccessRoute(userRoles: Role[], allowedRoles: Role[]) {
  return userRoles.some((role) => allowedRoles.includes(role));
}
```

Create `frontend/src/auth/roles.ts`:

```ts
import type { Role } from './authStore';

export const staffRoles: Role[] = ['ADMIN', 'TREASURER', 'PASTOR', 'MEMBERSHIP', 'VIEWER'];
export const financeRoles: Role[] = ['ADMIN', 'TREASURER'];
export const membershipRoles: Role[] = ['ADMIN', 'MEMBERSHIP'];
export const reportRoles: Role[] = ['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'];
export const selfServiceRoles: Role[] = ['ADMIN', 'MEMBER'];
```

- [ ] **Step 4: Add HTTP client**

Create `frontend/src/api/http.ts`:

```ts
export async function postJson<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Request failed.' }));
    throw new Error(error.message ?? 'Request failed.');
  }

  return response.json() as Promise<TResponse>;
}
```

- [ ] **Step 5: Add router**

Create `frontend/src/router/index.ts`:

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { authState, canAccessRoute, type Role } from '../auth/authStore';
import { financeRoles, membershipRoles, reportRoles, selfServiceRoles, staffRoles } from '../auth/roles';
import LoginView from '../views/LoginView.vue';
import ChangePasswordView from '../views/ChangePasswordView.vue';
import DashboardView from '../views/DashboardView.vue';

const protectedPlaceholder = {
  template: '<section><h2>{{ title }}</h2></section>',
  props: ['title'],
};

const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView },
  { path: '/change-password', component: ChangePasswordView },
  { path: '/', component: DashboardView, meta: { roles: staffRoles } },
  { path: '/members', component: protectedPlaceholder, props: { title: 'Members' }, meta: { roles: membershipRoles } },
  { path: '/offerings', component: protectedPlaceholder, props: { title: 'Offerings' }, meta: { roles: financeRoles } },
  { path: '/finance', component: protectedPlaceholder, props: { title: 'Finance' }, meta: { roles: financeRoles } },
  { path: '/budgets', component: protectedPlaceholder, props: { title: 'Budgets' }, meta: { roles: financeRoles } },
  { path: '/reference-data', component: protectedPlaceholder, props: { title: 'Reference Data' }, meta: { roles: ['ADMIN', 'TREASURER', 'MEMBERSHIP'] as Role[] } },
  { path: '/reports', component: protectedPlaceholder, props: { title: 'Reports' }, meta: { roles: reportRoles } },
  { path: '/profile', component: protectedPlaceholder, props: { title: 'My Profile' }, meta: { roles: selfServiceRoles } },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.path === '/login') {
    return true;
  }

  if (!authState.currentUser) {
    return '/login';
  }

  if (authState.currentUser.mustChangePassword && to.path !== '/change-password') {
    return '/change-password';
  }

  const allowedRoles = to.meta.roles as Role[] | undefined;
  if (allowedRoles && !canAccessRoute(authState.currentUser.roles, allowedRoles)) {
    return '/';
  }

  return true;
});
```

- [ ] **Step 6: Add login and password views**

Create `frontend/src/views/LoginView.vue`:

```vue
<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="login">
      <h1>Church Operations</h1>
      <label>
        Login ID
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        Password
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">Sign in</button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { postJson } from '../api/http';
import { setCurrentUser, type CurrentUser } from '../auth/authStore';

const router = useRouter();
const username = ref('admin');
const password = ref('password');
const error = ref('');

async function login() {
  error.value = '';
  try {
    const user = await postJson<{ username: string; password: string }, CurrentUser>('/api/auth/login', {
      username: username.value,
      password: password.value,
    });
    setCurrentUser(user);
    await router.push(user.mustChangePassword ? '/change-password' : '/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Login failed.';
  }
}
</script>
```

Create `frontend/src/views/ChangePasswordView.vue`:

```vue
<template>
  <main class="auth-page">
    <form class="auth-panel" @submit.prevent="changePassword">
      <h1>Change Password</h1>
      <label>
        Current password
        <input v-model="currentPassword" type="password" autocomplete="current-password" />
      </label>
      <label>
        New password
        <input v-model="newPassword" type="password" autocomplete="new-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">Update password</button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { postJson } from '../api/http';
import { authState, setCurrentUser } from '../auth/authStore';

const router = useRouter();
const currentPassword = ref('');
const newPassword = ref('');
const error = ref('');

async function changePassword() {
  error.value = '';
  if (!authState.currentUser) {
    await router.push('/login');
    return;
  }
  try {
    await postJson('/api/auth/change-password', {
      username: authState.currentUser.primaryEmail,
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    setCurrentUser({ ...authState.currentUser, mustChangePassword: false });
    await router.push('/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Password change failed.';
  }
}
</script>
```

- [ ] **Step 7: Add dashboard shell**

Create `frontend/src/layouts/AppLayout.vue`:

```vue
<template>
  <div class="layout">
    <aside class="sidebar">
      <h1>Church Operations</h1>
      <RouterLink to="/">Dashboard</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'MEMBERSHIP'])" to="/members">Members</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/offerings">Offerings</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/finance">Finance</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER'])" to="/budgets">Budgets</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'MEMBERSHIP'])" to="/reference-data">Reference Data</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'TREASURER', 'PASTOR', 'VIEWER'])" to="/reports">Reports</RouterLink>
      <RouterLink v-if="hasAny(['ADMIN', 'MEMBER'])" to="/profile">My Profile</RouterLink>
    </aside>
    <section class="content">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { authState, type Role } from '../auth/authStore';

function hasAny(roles: Role[]) {
  return authState.currentUser?.roles.some((role) => roles.includes(role)) ?? false;
}
</script>
```

Create `frontend/src/views/DashboardView.vue`:

```vue
<template>
  <AppLayout>
    <h2>Dashboard</h2>
    <p>Welcome to Church Operations.</p>
  </AppLayout>
</template>

<script setup lang="ts">
import AppLayout from '../layouts/AppLayout.vue';
</script>
```

- [ ] **Step 8: Wire router into app**

Modify `frontend/src/main.ts`:

```ts
import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router';
import './styles/main.css';

createApp(App).use(router).mount('#app');
```

Modify `frontend/src/App.vue`:

```vue
<template>
  <RouterView />
</template>
```

Create `frontend/src/styles/main.css`:

```css
body {
  margin: 0;
  font-family: Arial, sans-serif;
  color: #1f2933;
  background: #f7f8fa;
}

button,
input {
  font: inherit;
}

.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-panel {
  width: min(420px, 100%);
  display: grid;
  gap: 16px;
  padding: 24px;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: white;
}

.auth-panel label {
  display: grid;
  gap: 6px;
}

.auth-panel input {
  min-height: 40px;
  border: 1px solid #c8d0d9;
  border-radius: 6px;
  padding: 0 10px;
}

.auth-panel button {
  min-height: 42px;
  border: 0;
  border-radius: 6px;
  background: #22577a;
  color: white;
}

.error {
  color: #b42318;
}

.layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px 1fr;
}

.sidebar {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 20px;
  background: #123047;
  color: white;
}

.sidebar a {
  color: white;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 6px;
}

.sidebar a.router-link-active {
  background: #22577a;
}

.content {
  padding: 28px;
}
```

- [ ] **Step 9: Run frontend tests**

Run: `cd frontend && npm test -- authStore.test.ts`

Expected: PASS.

- [ ] **Step 10: Run frontend build**

Run: `cd frontend && npm run build`

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add frontend
git commit -m "feat: add Vue auth shell"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Task 6: Foundation Integration Verification

**Files:**
- Modify: `README.md`
- Test: local Docker Compose runtime

**Interfaces:**
- Consumes: all outputs from Tasks 1-5.
- Produces: documented local run instructions.

- [ ] **Step 1: Create README**

Create or replace `README.md`:

```markdown
# Church Operations App

Multi-user church operations web app for members, offerings, finance, budgets, reports, and reference data.

## Local Run

```bash
docker compose up --build
```

Open:

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/actuator/health

Initial login:

- Username: `admin`
- Password: `password`

The first login requires a password change before using the app.

## Configuration

Church information is configured in `backend/src/main/resources/application.yml` or environment variables:

- `CHURCH_ADDRESS`
- `CHURCH_CONTACT_INFO`
- `CHURCH_TREASURER_NAME`

Branding assets are served from `backend/src/main/resources/static/branding`.
```

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 3: Run frontend tests**

Run: `cd frontend && npm test`

Expected: PASS.

- [ ] **Step 4: Run Docker Compose**

Run: `docker compose up --build`

Expected:

- MongoDB starts without errors.
- Backend starts on port `8080`.
- Frontend starts on port `5173`.
- `http://localhost:8080/actuator/health` returns status `UP`.
- `http://localhost:5173` displays the login screen.

- [ ] **Step 5: Manual login smoke test**

In the browser:

1. Open `http://localhost:5173`.
2. Log in with username `admin` and password `password`.
3. Confirm redirect to `/change-password`.
4. Change password to `password123`.
5. Confirm redirect to dashboard.

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "docs: add local run instructions"
```

If the workspace is not a git repository, skip the commit and record that in the task handoff.

## Follow-Up Plans

After this foundation plan is complete, create and execute these separate plans:

1. Membership and reference data management.
2. Finance/offering reference data expansion with parent-linked financial sub-categories: `docs/superpowers/plans/2026-07-08-reference-data-finance-offering-expansion.md`.
3. Offering management and automatic income transaction creation.
4. Finance expenses, approvals, cheque clearing, attachments, and audit entries.
5. Fiscal-year budgets and budget maintenance UI.
6. Reports: weekly offering status, member offering summary, official tax return extraction, and financial budget report.
7. Member self-service profile and offering history.

## Self-Review

Spec coverage in this foundation plan:

- Covered: Docker Compose local run, Java 21 backend scaffold, Vue 3 frontend scaffold, MongoDB connection, church information config, branding resource path, role enum, member identity model, seeded admin login, forced first password change, and role-aware navigation.
- Deferred by explicit follow-up plans: full CRUD for members, offerings, finance transactions, budgets, attachments, reports, audit history, and member self-service data screens.

Placeholder scan:

- The plan contains no unresolved placeholder markers or unspecified validation steps.

Type consistency:

- Java role names match Vue role names.
- `primaryEmail`, `mustChangePassword`, and role names match between backend and frontend.
- Church information property names match `application.yml` and the API response.
- Java package declarations follow `com.church.operation.config`, `dto`, `entity`, `exception`, `repo`, `rest`, `service`, and `util`; `filter` is reserved for later security filters.
