package com.church.operation.dto;

import com.church.operation.entity.ReferenceData;
import com.church.operation.util.ReferenceDataType;

public record ReferenceDataResponse(
    String id,
    ReferenceDataType type,
    String code,
    String label,
    int sortOrder,
    boolean active
) {
    public static ReferenceDataResponse from(ReferenceData referenceData) {
        return new ReferenceDataResponse(
            referenceData.getId(),
            referenceData.getType(),
            referenceData.getCode(),
            referenceData.getLabel(),
            referenceData.getSortOrder(),
            referenceData.isActive()
        );
    }
}
