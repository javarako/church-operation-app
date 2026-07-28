package com.church.operation.service;

import com.church.operation.config.PasswordResetProperties;
import com.church.operation.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetEmailServiceTest {
    @Mock private RuntimeEmailSender sender;

    @Test
    void sendsExistingResetMessageThroughRuntimeSettings() {
        PasswordResetEmailService service = new PasswordResetEmailService(
            sender,
            new PasswordResetProperties("https://church.test", Duration.ofMinutes(30), "ignored@test.org")
        );
        Member member = new Member();
        member.setPrimaryEmail("member@example.org");

        service.sendResetEmail(member, "raw token");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(
            org.mockito.ArgumentMatchers.eq("member@example.org"),
            org.mockito.ArgumentMatchers.eq("Reset your Church Operations password"),
            body.capture()
        );
        assertThat(body.getValue()).contains("https://church.test/reset-password?token=raw%20token");
    }
}
