package com.church.operation.service;

import com.church.operation.dto.ReferenceDataRequest;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        verify(referenceDataRepository).existsByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE");
        verify(referenceDataRepository).existsByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE");
        verify(referenceDataRepository).existsByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES");
        verify(referenceDataRepository, org.mockito.Mockito.times(25)).save(any(ReferenceData.class));
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

    @Test
    void membershipManagerCreatesReferenceData() {
        Member actor = member(Role.MEMBERSHIP);
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.GROUP_CODE,
            "CHOIR",
            "Choir",
            50,
            true,
            null
        );
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.GROUP_CODE, "CHOIR")).thenReturn(Optional.empty());
        when(referenceDataRepository.save(any(ReferenceData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        ReferenceData created = service.create(actor, request);

        assertThat(created.getType()).isEqualTo(ReferenceDataType.GROUP_CODE);
        assertThat(created.getCode()).isEqualTo("CHOIR");
        assertThat(created.getLabel()).isEqualTo("Choir");
        assertThat(created.getSortOrder()).isEqualTo(50);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getParentCode()).isNull();
    }

    @Test
    void viewerCannotCreateReferenceData() {
        Member actor = member(Role.VIEWER);
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.MEMBERSHIP_STATUS,
            "WATCHING",
            "Watching",
            50,
            true,
            null
        );

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        assertThatThrownBy(() -> service.create(actor, request))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to maintain reference data.");
    }

    @Test
    void adminUpdatesReferenceData() {
        Member actor = member(Role.ADMIN);
        ReferenceData existing = new ReferenceData();
        existing.setId("ref-id");
        existing.setType(ReferenceDataType.MEMBERSHIP_STATUS);
        existing.setCode("VISITOR");
        existing.setLabel("Visitor");
        existing.setSortOrder(30);
        existing.setActive(true);

        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.MEMBERSHIP_STATUS,
            "VISITOR",
            "Guest",
            25,
            false,
            null
        );

        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(existing));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.MEMBERSHIP_STATUS, "VISITOR")).thenReturn(Optional.of(existing));
        when(referenceDataRepository.save(existing)).thenReturn(existing);

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        ReferenceData updated = service.update(actor, "ref-id", request);

        assertThat(updated.getLabel()).isEqualTo("Guest");
        assertThat(updated.getSortOrder()).isEqualTo(25);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void financialSubCategoryRequiresParentCode() {
        Member actor = member(Role.TREASURER);
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY,
            "SUPPLIES",
            "Supplies",
            10,
            true,
            " "
        );

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        assertThatThrownBy(() -> service.create(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parent financial category is required for financial sub-category.");
    }

    @Test
    void financialSubCategoryRequiresExistingFinancialCategoryParent() {
        Member actor = member(Role.TREASURER);
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY,
            "SUPPLIES",
            "Supplies",
            10,
            true,
            "office"
        );

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE")).thenReturn(Optional.empty());

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        assertThatThrownBy(() -> service.create(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parent financial category was not found.");
    }

    @Test
    void createsFinancialSubCategoryWithNormalizedParentCode() {
        Member actor = member(Role.TREASURER);
        ReferenceData parent = new ReferenceData();
        parent.setId("parent-id");
        parent.setType(ReferenceDataType.FINANCIAL_CATEGORY);
        parent.setCode("OFFICE");
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY,
            "supplies",
            "Supplies",
            10,
            true,
            "office"
        );

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE")).thenReturn(Optional.of(parent));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES")).thenReturn(Optional.empty());
        when(referenceDataRepository.save(any(ReferenceData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        ReferenceData created = service.create(actor, request);

        assertThat(created.getCode()).isEqualTo("SUPPLIES");
        assertThat(created.getParentCode()).isEqualTo("OFFICE");
    }

    @Test
    void clearsParentCodeForNonSubCategoryTypes() {
        Member actor = member(Role.TREASURER);
        ReferenceDataRequest request = new ReferenceDataRequest(
            ReferenceDataType.FINANCIAL_CATEGORY,
            "office",
            "Office",
            10,
            true,
            "IGNORED"
        );

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE")).thenReturn(Optional.empty());
        when(referenceDataRepository.save(any(ReferenceData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        ReferenceData created = service.create(actor, request);

        assertThat(created.getParentCode()).isNull();
    }

    @Test
    void listsActiveFinancialSubCategoriesByParentCode() {
        ReferenceData supplies = new ReferenceData();
        supplies.setType(ReferenceDataType.FINANCIAL_SUB_CATEGORY);
        supplies.setCode("SUPPLIES");
        supplies.setParentCode("OFFICE");

        when(referenceDataRepository.findByTypeAndParentCodeAndActiveTrueOrderBySortOrderAscLabelAsc(
            ReferenceDataType.FINANCIAL_SUB_CATEGORY,
            "OFFICE"
        )).thenReturn(List.of(supplies));

        ReferenceDataService service = new ReferenceDataService(referenceDataRepository);

        assertThat(service.listActive(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "office")).containsExactly(supplies);
    }

    private Member member(Role role) {
        Member member = new Member();
        member.setRoles(Set.of(role));
        member.setActive(true);
        return member;
    }
}
