package com.church.operation.service;

import com.church.operation.exception.EmailDeliveryException;
import jakarta.mail.SendFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Function;

@Service
public class RuntimeEmailSender {
    private final EmailSettingsResolver resolver;
    private final Function<EffectiveEmailSettings, JavaMailSender> senderFactory;

    @Autowired
    public RuntimeEmailSender(EmailSettingsResolver resolver) {
        this(resolver, RuntimeEmailSender::buildSender);
    }

    RuntimeEmailSender(
        EmailSettingsResolver resolver,
        Function<EffectiveEmailSettings, JavaMailSender> senderFactory
    ) {
        this.resolver = resolver;
        this.senderFactory = senderFactory;
    }

    public void send(String recipient, String subject, String body) {
        send(resolver.resolve(), recipient, subject, body);
    }

    public void send(EffectiveEmailSettings settings, String recipient, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(settings.fromAddress());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);
            senderFactory.apply(settings).send(message);
        } catch (MailAuthenticationException exception) {
            throw new EmailDeliveryException(EmailDeliveryException.Category.AUTHENTICATION);
        } catch (MailException exception) {
            throw categorize(exception);
        } finally {
            settings.clearPassword();
        }
    }

    private EmailDeliveryException categorize(MailException exception) {
        if (hasCause(exception, SSLException.class)) {
            return new EmailDeliveryException(EmailDeliveryException.Category.TLS);
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, SocketTimeoutException.class)) {
            return new EmailDeliveryException(EmailDeliveryException.Category.CONNECTION);
        }
        if (exception instanceof MailSendException && hasCause(exception, SendFailedException.class)) {
            return new EmailDeliveryException(EmailDeliveryException.Category.RECIPIENT_REJECTED);
        }
        return new EmailDeliveryException(EmailDeliveryException.Category.DELIVERY);
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static JavaMailSender buildSender(EffectiveEmailSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setUsername(settings.username());
        char[] password = settings.password();
        try {
            sender.setPassword(new String(password));
        } finally {
            Arrays.fill(password, '\0');
        }
        Properties mail = sender.getJavaMailProperties();
        mail.setProperty("mail.smtp.auth", Boolean.toString(settings.auth()));
        mail.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.startTls()));
        mail.setProperty("mail.smtp.connectiontimeout", "10000");
        mail.setProperty("mail.smtp.timeout", "10000");
        mail.setProperty("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
