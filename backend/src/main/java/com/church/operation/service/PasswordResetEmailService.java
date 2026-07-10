package com.church.operation.service;

import com.church.operation.config.PasswordResetProperties;
import com.church.operation.entity.Member;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetEmailService {
    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public PasswordResetEmailService(JavaMailSender mailSender, PasswordResetProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendResetEmail(Member member, String rawToken) {
        String resetLink = UriComponentsBuilder
            .fromUriString(properties.frontendBaseUrl())
            .path("/reset-password")
            .queryParam("token", rawToken)
            .build()
            .encode()
            .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(member.getPrimaryEmail());
        message.setSubject("Reset your Church Operations password");
        message.setText("Use this link within 30 minutes to set a new password:\n\n" + resetLink
            + "\n\nIf you did not request this change, you can ignore this email.");
        mailSender.send(message);
    }
}
