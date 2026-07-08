package com.church.operation.service;

import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import org.springframework.stereotype.Service;

import java.util.List;

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
    }

    public List<ReferenceData> listActive(ReferenceDataType type) {
        return referenceDataRepository.findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(type);
    }

    private void seed(ReferenceDataType type, String code, String label, int sortOrder) {
        if (referenceDataRepository.existsByTypeAndCode(type, code)) {
            return;
        }
        ReferenceData referenceData = new ReferenceData();
        referenceData.setType(type);
        referenceData.setCode(code);
        referenceData.setLabel(label);
        referenceData.setSortOrder(sortOrder);
        referenceData.setActive(true);
        referenceDataRepository.save(referenceData);
    }
}
