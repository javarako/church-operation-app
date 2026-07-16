package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.SystemAuditEvent;
import com.church.operation.repo.SystemAuditEventRepository;
import com.church.operation.util.SystemAuditOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemAuditServiceTest {
    private final SystemAuditEventRepository repository = mock(SystemAuditEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T20:00:00Z"), ZoneOffset.UTC);
    private final SystemAuditService service = new SystemAuditService(repository, clock);

    @Test
    void recordsImmutableSuccessWithActorAndAllowListedMetadata() {
        service.recordSuccess(admin(), SystemAuditOperation.FISCAL_CLEAN, Map.of(
            "fiscalYear", 2026,
            "recordCount", 33,
            "operationId", "archive-operation"
        ));

        ArgumentCaptor<SystemAuditEvent> event = ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getActorId()).isEqualTo("admin-id");
        assertThat(event.getValue().getActorEmail()).isEqualTo("admin@church.local");
        assertThat(event.getValue().getOperation()).isEqualTo(SystemAuditOperation.FISCAL_CLEAN);
        assertThat(event.getValue().getTimestamp()).isEqualTo(clock.instant());
        assertThat(event.getValue().getResult()).isEqualTo("SUCCESS");
        assertThat(event.getValue().getMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "fiscalYear", "2026",
            "recordCount", "33",
            "operationId", "archive-operation"
        ));
        assertThat(event.getValue().getErrorSummary()).isNull();
        assertThatThrownBy(() -> event.getValue().getMetadata().put("memberId", "changed"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordsSanitizedAndTruncatedFailureSummary() {
        String longMessage = "Restore failed\npassword=secret " + "x".repeat(400);

        service.recordFailure(
            admin(), SystemAuditOperation.FULL_RESTORE_EXECUTE,
            Map.of("operationId", "restore-operation"), new IllegalStateException(longMessage)
        );

        ArgumentCaptor<SystemAuditEvent> event = ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getResult()).isEqualTo("FAILURE");
        assertThat(event.getValue().getErrorSummary())
            .doesNotContain("secret")
            .doesNotContain("\n")
            .hasSizeLessThanOrEqualTo(300);
    }

    @Test
    void rejectsSensitiveOrUnknownMetadataKeys() {
        for (String key : new String[] {"passwordHint", "sourceHash", "imageBytes", "zipContent", "unknown"}) {
            assertThatThrownBy(() -> service.recordSuccess(
                admin(), SystemAuditOperation.FULL_BACKUP, Map.of(key, "value")
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void auditPersistenceFailureDoesNotReplaceThePrimaryOperationResult() {
        doThrow(new IllegalStateException("audit database unavailable")).when(repository).save(any());

        assertThatCode(() -> service.recordSuccess(
            admin(), SystemAuditOperation.MEMBER_DELETE, Map.of("memberId", "member-1")
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.recordFailure(
            admin(), SystemAuditOperation.MEMBER_DELETE, Map.of("memberId", "member-1"),
            new IllegalStateException("member delete failed")
        )).doesNotThrowAnyException();
    }

    private Member admin() {
        Member member = new Member();
        member.setId("admin-id");
        member.setPrimaryEmail("admin@church.local");
        return member;
    }
}
