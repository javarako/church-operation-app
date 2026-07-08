package com.church.operation.service;

import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void seedsDefaultGroupCodesAndMembershipStatusesWhenMissing() {
        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        service.seedDefaults();

        verify(referenceDataRepository).existsByTypeAndCode(ReferenceDataType.GROUP_CODE, "ADULT");
        verify(referenceDataRepository).existsByTypeAndCode(ReferenceDataType.MEMBERSHIP_STATUS, "ACTIVE");
        verify(referenceDataRepository, org.mockito.Mockito.times(8)).save(any(ReferenceData.class));
    }

    @Test
    void doesNotOverwriteExistingDefaultReferenceData() {
        when(referenceDataRepository.existsByTypeAndCode(ReferenceDataType.GROUP_CODE, "ADULT")).thenReturn(true);

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        service.seedDefaults();

        verify(referenceDataRepository, never()).save(org.mockito.ArgumentMatchers.argThat(referenceData ->
            referenceData.getType() == ReferenceDataType.GROUP_CODE && "ADULT".equals(referenceData.getCode())
        ));
    }

    @Test
    void listsOnlyActiveReferenceDataForType() {
        ReferenceData adult = new ReferenceData();
        adult.setType(ReferenceDataType.GROUP_CODE);
        adult.setCode("ADULT");
        adult.setLabel("Adult");
        adult.setActive(true);

        when(referenceDataRepository.findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(ReferenceDataType.GROUP_CODE))
            .thenReturn(List.of(adult));

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        assertThat(service.listActive(ReferenceDataType.GROUP_CODE)).containsExactly(adult);
    }
}
