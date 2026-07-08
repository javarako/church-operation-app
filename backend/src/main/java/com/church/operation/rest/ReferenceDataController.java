package com.church.operation.rest;

import com.church.operation.dto.ReferenceDataResponse;
import com.church.operation.service.ReferenceDataService;
import com.church.operation.util.ReferenceDataType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reference-data")
public class ReferenceDataController {
    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/{type}")
    List<ReferenceDataResponse> listReferenceData(@PathVariable("type") ReferenceDataType type) {
        return referenceDataService.listActive(type).stream()
            .map(ReferenceDataResponse::from)
            .toList();
    }
}
