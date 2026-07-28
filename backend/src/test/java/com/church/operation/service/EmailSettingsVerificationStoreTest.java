package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSettingsVerificationStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-22T15:00:00Z");

    @Test
    void springCanConstructTheVerificationStoreBean() {
        RuntimeEmailProperties properties = properties();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuntimeEmailProperties.class, () -> properties);
            context.register(EmailSettingsVerificationStore.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(EmailSettingsVerificationStore.class)).isNotNull();
        }
    }

    @Test
    void tokenIsActorBoundFingerprintBoundAndSingleUse() {
        EmailSettingsVerificationStore store = store(Clock.fixed(NOW, ZoneOffset.UTC));
        var token = store.remember("admin-1", new byte[] {1, 2, 3});

        store.consume(token.token(), "admin-1", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> store.consume(token.token(), "admin-1", new byte[] {1, 2, 3}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Test the email settings again before saving.");
    }

    @Test
    void rejectsDifferentActorChangedSettingsAndExpiredToken() {
        MutableClock clock = new MutableClock(NOW);
        EmailSettingsVerificationStore store = store(clock);
        var actorToken = store.remember("admin-1", new byte[] {1});
        assertThatThrownBy(() -> store.consume(actorToken.token(), "admin-2", new byte[] {1}))
            .isInstanceOf(SecurityException.class);

        var changedToken = store.remember("admin-1", new byte[] {1});
        assertThatThrownBy(() -> store.consume(changedToken.token(), "admin-1", new byte[] {2}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Test the email settings again before saving.");

        var expiredToken = store.remember("admin-1", new byte[] {1});
        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> store.consume(expiredToken.token(), "admin-1", new byte[] {1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Test the email settings again before saving.");
    }

    private EmailSettingsVerificationStore store(Clock clock) {
        return new EmailSettingsVerificationStore(properties(), clock);
    }

    private RuntimeEmailProperties properties() {
        return new RuntimeEmailProperties(
            "localhost", 1025, "", "", false, false, "from@test.org", "", Duration.ofMinutes(10)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
