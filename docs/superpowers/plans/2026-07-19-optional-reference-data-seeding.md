# Optional Reference Data Seeding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Disable default reference-data seeding unless the deployment explicitly enables it.

**Architecture:** Keep the existing idempotent seed implementation in `ReferenceDataService`. Control whether its startup runner exists with Spring Boot's `@ConditionalOnProperty`, using a default-false YAML property and environment override.

**Tech Stack:** Java 21, Spring Boot 4, JUnit 5, Spring Boot Test, Mockito

## Global Constraints

- `church.reference-data.seed-defaults` defaults to `false`.
- `CHURCH_REFERENCE_DATA_SEED_DEFAULTS=true` enables seeding through the environment.
- Disabled or missing configuration must not create the startup runner.
- Existing reference data must never be deleted, disabled, or modified.
- Enabled seeding retains the existing insert-only behavior for missing type-and-code records.

---

### Task 1: Conditional Reference Data Bootstrap

**Files:**
- Create: `backend/src/test/java/com/church/operation/service/ReferenceDataBootstrapRunnerTest.java`
- Modify: `backend/src/main/java/com/church/operation/service/ReferenceDataBootstrapRunner.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `ReferenceDataService.seedDefaults()`
- Produces: `ReferenceDataBootstrapRunner` only when `church.reference-data.seed-defaults=true`

- [x] **Step 1: Write failing conditional-context tests**

Create `ReferenceDataBootstrapRunnerTest`:

```java
package com.church.operation.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReferenceDataBootstrapRunnerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void createsRunnerWhenDefaultSeedingIsEnabled() {
        contextRunner
            .withPropertyValues("church.reference-data.seed-defaults=true")
            .run(context -> assertThat(context)
                .hasSingleBean(ReferenceDataBootstrapRunner.class));
    }

    @Test
    void doesNotCreateRunnerWhenDefaultSeedingIsDisabled() {
        contextRunner
            .withPropertyValues("church.reference-data.seed-defaults=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(ReferenceDataBootstrapRunner.class));
    }

    @Test
    void doesNotCreateRunnerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context)
            .doesNotHaveBean(ReferenceDataBootstrapRunner.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ReferenceDataBootstrapRunner.class)
    static class TestConfig {
        @Bean
        ReferenceDataService referenceDataService() {
            return mock(ReferenceDataService.class);
        }
    }
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd backend
mvn -Dtest=ReferenceDataBootstrapRunnerTest test
```

Expected: the disabled and missing-property tests fail because the runner is
currently unconditional.

- [x] **Step 3: Make the runner conditional**

Add this import and annotation to `ReferenceDataBootstrapRunner`:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@Order(10)
@ConditionalOnProperty(
    prefix = "church.reference-data",
    name = "seed-defaults",
    havingValue = "true",
    matchIfMissing = false
)
public class ReferenceDataBootstrapRunner implements ApplicationRunner {
```

- [x] **Step 4: Add the default-false application setting**

Add this section beneath `church:` in `application.yml`:

```yaml
  reference-data:
    seed-defaults: ${CHURCH_REFERENCE_DATA_SEED_DEFAULTS:false}
```

- [x] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=ReferenceDataBootstrapRunnerTest,ReferenceDataServiceTest test
```

Expected: all conditional-runner and existing seed-service tests pass.

- [x] **Step 6: Run full backend verification**

Run:

```bash
cd backend
mvn test
```

Expected: all backend tests pass, including Docker-backed MongoDB integration
tests.

Actual: all 253 tests outside `QuarterlyFinancialExcelServiceTest` pass,
including Docker-backed integration tests. The complete 259-test run has two
pre-existing failures because commit `bec0c61` changed quarterly workbook
column B from `28.83203125` to `28.5` and column J from `16.83203125` to
`16.5` without updating that test class.

- [x] **Step 7: Verify the final diff**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only intended project changes remain.
