package com.church.operation.rest;

import com.church.operation.dto.EmailSettingsResetRequest;
import com.church.operation.dto.EmailSettingsResponse;
import com.church.operation.dto.EmailSettingsSaveRequest;
import com.church.operation.dto.EmailSettingsTestRequest;
import com.church.operation.dto.EmailSettingsTestResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.EmailSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/email-settings")
public class EmailSettingsController {
    private final EmailSettingsService service;

    public EmailSettingsController(EmailSettingsService service) {
        this.service = service;
    }

    @GetMapping
    EmailSettingsResponse get(Authentication authentication) {
        return service.get(actor(authentication));
    }

    @PostMapping("/test")
    EmailSettingsTestResponse test(
        Authentication authentication,
        @Valid @RequestBody EmailSettingsTestRequest request
    ) {
        return service.test(actor(authentication), request);
    }

    @PutMapping
    EmailSettingsResponse save(
        Authentication authentication,
        @Valid @RequestBody EmailSettingsSaveRequest request
    ) {
        return service.save(actor(authentication), request);
    }

    @PostMapping("/reset")
    EmailSettingsResponse reset(
        Authentication authentication,
        @Valid @RequestBody EmailSettingsResetRequest request
    ) {
        return service.reset(actor(authentication), request);
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
