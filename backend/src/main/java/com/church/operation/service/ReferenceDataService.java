package com.church.operation.service;

import com.church.operation.dto.ReferenceDataRequest;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ReferenceDataService {
    private final ReferenceDataRepository referenceDataRepository;

    public ReferenceDataService(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }

    public void seedDefaults() {
        seed(ReferenceDataType.GROUP_CODE, "ADULT", "Adult", 10);
        seed(ReferenceDataType.GROUP_CODE, "YOUTH", "Youth", 20);
        seed(ReferenceDataType.GROUP_CODE, "CHILDREN", "Children", 30);
        seed(ReferenceDataType.GROUP_CODE, "SENIOR", "Senior", 40);

        seed(ReferenceDataType.MEMBERSHIP_STATUS, "ACTIVE", "Active", 10);
        seed(ReferenceDataType.MEMBERSHIP_STATUS, "INACTIVE", "Inactive", 20);
        seed(ReferenceDataType.MEMBERSHIP_STATUS, "VISITOR", "Visitor", 30);
        seed(ReferenceDataType.MEMBERSHIP_STATUS, "TRANSFERRED", "Transferred", 40);

        seed(ReferenceDataType.OFFERING_FUND, "GENERAL", "General Fund", 10);
        seed(ReferenceDataType.OFFERING_CATEGORY, "TITHE", "Tithe", 10, "GENERAL");
        seed(ReferenceDataType.OFFERING_CATEGORY, "THANKSGIVING", "Thanksgiving", 20, "GENERAL");
        seed(ReferenceDataType.OFFERING_CATEGORY, "MISSION", "Mission", 30, "GENERAL");
        seed(ReferenceDataType.OFFERING_CATEGORY, "BUILDING", "Building", 40, "GENERAL");

        seed(ReferenceDataType.PAYMENT_METHOD, "CASH", "Cash", 10);
        seed(ReferenceDataType.PAYMENT_METHOD, "CHEQUE", "Cheque", 20);
        seed(ReferenceDataType.PAYMENT_METHOD, "E_TRANSFER", "E-Transfer", 30);
        seed(ReferenceDataType.PAYMENT_METHOD, "CREDIT_CARD", "Credit Card", 40);

        seed(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE", "Office", 10);
        seed(ReferenceDataType.FINANCIAL_CATEGORY, "MINISTRY", "Ministry", 20);
        seed(ReferenceDataType.FINANCIAL_CATEGORY, "FACILITY", "Facility", 30);
        seed(ReferenceDataType.FINANCIAL_CATEGORY, "MISSIONS", "Missions", 40);

        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES", "Supplies", 10, "OFFICE");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "PRINTING", "Printing", 20, "OFFICE");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "UTILITIES", "Utilities", 10, "FACILITY");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "REPAIR", "Repair", 20, "FACILITY");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "RENT", "Rent", 30, "FACILITY");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "EVENT", "Event", 10, "MINISTRY");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "MATERIALS", "Materials", 20, "MINISTRY");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "LOCAL_OUTREACH", "Local Outreach", 10, "MISSIONS");
        seed(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "OVERSEAS_SUPPORT", "Overseas Support", 20, "MISSIONS");
    }

    public List<ReferenceData> listActive(ReferenceDataType type) {
        return referenceDataRepository.findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(type);
    }

    public List<ReferenceData> listActive(ReferenceDataType type, String parentCode) {
        String normalizedParentCode = trimToNull(parentCode);
        if (isChildType(type) && normalizedParentCode != null) {
            return referenceDataRepository.findByTypeAndParentCodeAndActiveTrueOrderBySortOrderAscLabelAsc(
                type,
                normalizedParentCode.toUpperCase(Locale.ROOT)
            );
        }
        return listActive(type);
    }

    public List<ReferenceData> listAll(Member actor, ReferenceDataType type) {
        return listAll(actor, type, null);
    }

    public List<ReferenceData> listAll(Member actor, ReferenceDataType type, String parentCode) {
        requireReferenceDataManager(actor);
        String normalizedParentCode = trimToNull(parentCode);
        if (isChildType(type) && normalizedParentCode != null) {
            return referenceDataRepository.findByTypeAndParentCodeOrderBySortOrderAscLabelAsc(
                type,
                normalizedParentCode.toUpperCase(Locale.ROOT)
            );
        }
        return referenceDataRepository.findByTypeOrderBySortOrderAscLabelAsc(type);
    }

    public ReferenceData create(Member actor, ReferenceDataRequest request) {
        requireReferenceDataManager(actor);
        rejectLegacyType(request.type());
        ReferenceData referenceData = new ReferenceData();
        apply(referenceData, request);
        ensureUnique(referenceData);
        return referenceDataRepository.save(referenceData);
    }

    public ReferenceData update(Member actor, String id, ReferenceDataRequest request) {
        requireReferenceDataManager(actor);
        rejectLegacyType(request.type());
        ReferenceData referenceData = referenceDataRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reference data was not found."));
        String requestedCode = normalizeCode(request.code());
        if (referenceData.getType() != request.type()
            || !Objects.equals(referenceData.getCode(), requestedCode)) {
            throw new IllegalArgumentException("Reference data type and code cannot be changed after creation.");
        }
        apply(referenceData, request);
        ensureUnique(referenceData);
        return referenceDataRepository.save(referenceData);
    }

    private void seed(ReferenceDataType type, String code, String label, int sortOrder) {
        seed(type, code, label, sortOrder, null);
    }

    private void seed(ReferenceDataType type, String code, String label, int sortOrder, String parentCode) {
        if (referenceDataRepository.existsByTypeAndCode(type, code)) {
            return;
        }
        ReferenceData referenceData = new ReferenceData();
        referenceData.setType(type);
        referenceData.setCode(code);
        referenceData.setLabel(label);
        referenceData.setParentCode(parentCode);
        referenceData.setSortOrder(sortOrder);
        referenceData.setActive(true);
        referenceDataRepository.save(referenceData);
    }

    private void apply(ReferenceData referenceData, ReferenceDataRequest request) {
        String code = normalizeCode(request.code());
        String label = trimToNull(request.label());
        if (label == null) {
            throw new IllegalArgumentException("Reference data label is required.");
        }
        referenceData.setType(request.type());
        referenceData.setCode(code);
        referenceData.setLabel(label);
        referenceData.setParentCode(resolveParentCode(request.type(), request.parentCode()));
        referenceData.setSortOrder(request.sortOrder());
        referenceData.setActive(request.active());
    }

    private String resolveParentCode(ReferenceDataType type, String parentCode) {
        if (!isChildType(type)) {
            return null;
        }

        String normalizedParentCode = trimToNull(parentCode);
        if (normalizedParentCode == null) {
            if (type == ReferenceDataType.OFFERING_CATEGORY) {
                throw new IllegalArgumentException("Parent offering fund is required for offering category.");
            }
            throw new IllegalArgumentException("Parent financial category is required for financial sub-category.");
        }

        normalizedParentCode = normalizedParentCode.toUpperCase(Locale.ROOT);
        ReferenceDataType parentType = type == ReferenceDataType.OFFERING_CATEGORY
            ? ReferenceDataType.OFFERING_FUND
            : ReferenceDataType.FINANCIAL_CATEGORY;
        String missingMessage = type == ReferenceDataType.OFFERING_CATEGORY
            ? "Parent offering fund was not found."
            : "Parent financial category was not found.";
        referenceDataRepository.findByTypeAndCode(parentType, normalizedParentCode)
            .orElseThrow(() -> new IllegalArgumentException(missingMessage));
        return normalizedParentCode;
    }

    private void ensureUnique(ReferenceData referenceData) {
        referenceDataRepository.findByTypeAndCode(referenceData.getType(), referenceData.getCode())
            .filter(existing -> !Objects.equals(existing.getId(), referenceData.getId()))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Reference data code is already used for this type.");
            });
    }

    private void requireReferenceDataManager(Member actor) {
        if (!hasRole(actor, Role.ADMIN)) {
            throw new SecurityException("You do not have permission to maintain reference data.");
        }
    }

    private boolean isChildType(ReferenceDataType type) {
        return type == ReferenceDataType.OFFERING_CATEGORY
            || type == ReferenceDataType.FINANCIAL_SUB_CATEGORY;
    }

    private void rejectLegacyType(ReferenceDataType type) {
        if (type == ReferenceDataType.OFFERING_FUND_CATEGORY) {
            throw new IllegalArgumentException("The legacy combined offering reference type cannot be maintained.");
        }
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Reference data code is required.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
