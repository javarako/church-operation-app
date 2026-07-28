package com.church.operation.service;

import com.church.operation.config.PasswordResetProperties;
import com.church.operation.entity.Member;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetEmailService {
    private final RuntimeEmailSender emailSender;
    private final PasswordResetProperties properties;

    public PasswordResetEmailService(RuntimeEmailSender emailSender, PasswordResetProperties properties) {
        this.emailSender = emailSender;
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

        emailSender.send(
            member.getPrimaryEmail(),
            "Reset your Church Operations password",
            "Use this link within 30 minutes to set a new password:\n\n" + resetLink
                + "\n\nIf you did not request this change, you can ignore this email."
        );
    }
}
