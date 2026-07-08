package com.church.operation.rest;

import com.church.operation.dto.OfferingRequest;
import com.church.operation.dto.OfferingResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.OfferingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/offerings")
public class OfferingController {
    private final OfferingService offeringService;

    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @GetMapping
    List<OfferingResponse> listOfferings(Authentication authentication) {
        return offeringService.listOfferings(actor(authentication)).stream()
            .map(OfferingResponse::from)
            .toList();
    }

    @PostMapping
    OfferingResponse createOffering(Authentication authentication, @RequestBody OfferingRequest request) {
        return OfferingResponse.from(offeringService.createOffering(actor(authentication), request));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
