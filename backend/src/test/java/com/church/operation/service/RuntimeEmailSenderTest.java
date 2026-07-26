package com.church.operation.service;

import com.church.operation.exception.EmailDeliveryException;
import com.church.operation.util.EmailSettingsSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeEmailSenderTest {
    @Mock private EmailSettingsResolver resolver;
    @Mock private JavaMailSender mailSender;

    @Test
    void springCanConstructTheRuntimeEmailSenderBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmailSettingsResolver.class, () -> resolver);
            context.register(RuntimeEmailSender.class);

            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.getBean(RuntimeEmailSender.class)).isNotNull();
        }
    }

    @Test
    void resolvesEverySendAndUsesEffectiveSenderAddress() {
        EffectiveEmailSettings first = settings("first.smtp.test", "one", "first@test.org");
        EffectiveEmailSettings second = settings("second.smtp.test", "two", "second@test.org");
        when(resolver.resolve()).thenReturn(first, second);
        List<String> createdHosts = new ArrayList<>();
        Function<EffectiveEmailSettings, JavaMailSender> factory = settings -> {
            createdHosts.add(settings.host());
            return mailSender;
        };
        RuntimeEmailSender sender = new RuntimeEmailSender(resolver, factory);

        sender.send("admin@test.org", "First", "Body");
        sender.send("admin@test.org", "Second", "Body");

        assertThat(createdHosts).containsExactly("first.smtp.test", "second.smtp.test");
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(SimpleMailMessage::getFrom)
            .containsExactly("first@test.org", "second@test.org");
        assertThat(first.password()).containsOnly('\0');
        assertThat(second.password()).containsOnly('\0');
    }

    @Test
    void mapsAuthenticationFailureWithoutProviderDetails() {
        doThrow(new MailAuthenticationException("535 user secret rejected"))
            .when(mailSender).send(any(SimpleMailMessage.class));
        RuntimeEmailSender sender = new RuntimeEmailSender(resolver, ignored -> mailSender);

        assertThatThrownBy(() -> sender.send(settings("smtp.test", "secret", "from@test.org"),
            "admin@test.org", "Test", "Body"))
            .isInstanceOfSatisfying(EmailDeliveryException.class, failure -> {
                assertThat(failure.category()).isEqualTo(EmailDeliveryException.Category.AUTHENTICATION);
                assertThat(failure.getMessage()).isEqualTo("The email server rejected the configured credentials.");
                assertThat(failure.getMessage()).doesNotContain("user", "secret");
            });
    }

    @Test
    void mapsConnectionFailureWithoutNestedExceptionMessage() {
        doThrow(new MailSendException("cannot connect using secret", new ConnectException("smtp.internal")))
            .when(mailSender).send(any(SimpleMailMessage.class));
        RuntimeEmailSender sender = new RuntimeEmailSender(resolver, ignored -> mailSender);

        assertThatThrownBy(() -> sender.send(settings("smtp.test", "secret", "from@test.org"),
            "admin@test.org", "Test", "Body"))
            .isInstanceOfSatisfying(EmailDeliveryException.class, failure -> {
                assertThat(failure.category()).isEqualTo(EmailDeliveryException.Category.CONNECTION);
                assertThat(failure.getMessage()).doesNotContain("smtp.internal", "secret");
            });
    }

    private EffectiveEmailSettings settings(String host, String password, String fromAddress) {
        return new EffectiveEmailSettings(
            host, 587, "user", password.toCharArray(), true, true,
            fromAddress, EmailSettingsSource.DATABASE
        );
    }
}
