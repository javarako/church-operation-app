package com.church.operation.rest;

import com.church.operation.dto.ReferenceDataResponse;
import com.church.operation.dto.ReferenceDataRequest;
import com.church.operation.entity.Member;
import com.church.operation.service.ReferenceDataService;
import com.church.operation.service.ReferenceDataDeletionService;
import com.church.operation.util.ReferenceDataType;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/reference-data")
public class ReferenceDataController {
    private final ReferenceDataService referenceDataService;
    private final ReferenceDataDeletionService referenceDataDeletionService;

    public ReferenceDataController(
        ReferenceDataService referenceDataService,
        ReferenceDataDeletionService referenceDataDeletionService
    ) {
        this.referenceDataService = referenceDataService;
        this.referenceDataDeletionService = referenceDataDeletionService;
    }

    @GetMapping("/{type}")
    List<ReferenceDataResponse> listReferenceData(
        @PathVariable("type") ReferenceDataType type,
        @RequestParam(name = "parentCode", required = false) String parentCode
    ) {
        return referenceDataService.listActive(type, parentCode).stream()
            .map(ReferenceDataResponse::from)
            .toList();
    }

    @GetMapping("/maintenance/{type}")
    List<ReferenceDataResponse> listAllReferenceData(
        Authentication authentication,
        @PathVariable("type") ReferenceDataType type,
        @RequestParam(name = "parentCode", required = false) String parentCode
    ) {
        return referenceDataService.listAll(actor(authentication), type, parentCode).stream()
            .map(ReferenceDataResponse::from)
            .toList();
    }

    @PostMapping
    ReferenceDataResponse createReferenceData(Authentication authentication, @Valid @RequestBody ReferenceDataRequest request) {
        return ReferenceDataResponse.from(referenceDataService.create(actor(authentication), request));
    }

    @PutMapping("/{id}")
    ReferenceDataResponse updateReferenceData(
        Authentication authentication,
        @PathVariable("id") String id,
        @Valid @RequestBody ReferenceDataRequest request
    ) {
        return ReferenceDataResponse.from(referenceDataService.update(actor(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteReferenceData(Authentication authentication, @PathVariable("id") String id) {
        referenceDataDeletionService.delete(actor(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
