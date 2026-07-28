package com.church.operation.rest;

import com.church.operation.dto.ChurchSettingsAdminResponse;
import com.church.operation.dto.ChurchSettingsSaveRequest;
import com.church.operation.entity.Member;
import com.church.operation.service.ChurchSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/church-settings")
public class ChurchSettingsController {
    private final ChurchSettingsService service;

    public ChurchSettingsController(ChurchSettingsService service) {
        this.service = service;
    }

    @GetMapping
    ChurchSettingsAdminResponse get(Authentication authentication) {
        return service.get(actor(authentication));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ChurchSettingsAdminResponse save(
        Authentication authentication,
        @Valid @RequestPart("settings") ChurchSettingsSaveRequest request,
        @RequestPart(value = "logo", required = false) MultipartFile logo,
        @RequestPart(value = "banner", required = false) MultipartFile banner
    ) {
        return service.save(actor(authentication), request, logo, banner);
    }

    @PostMapping("/reset")
    ChurchSettingsAdminResponse reset(Authentication authentication) {
        return service.reset(actor(authentication));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
