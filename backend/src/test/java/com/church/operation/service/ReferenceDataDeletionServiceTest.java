package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataDeletionServiceTest {
    @Mock private ReferenceDataRepository referenceDataRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private SystemAuditService audit;

    @Test
    void deletesUnusedReferenceData() {
        Member actor = actor(Role.ADMIN);
        ReferenceData reference = reference(ReferenceDataType.PAYMENT_METHOD, "CASH");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));

        service().delete(actor, "ref-id");

        verify(referenceDataRepository).delete(reference);
        verify(audit).recordSuccess(
            eq(actor), eq(SystemAuditOperation.REFERENCE_DATA_DELETE), any(java.util.Map.class)
        );
    }

    @Test
    void nonAdminCannotDeleteReferenceData() {
        Member actor = actor(Role.TREASURER);

        assertThatThrownBy(() -> service().delete(actor, "ref-id"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to delete reference data.");
        verify(audit).recordFailure(
            eq(actor), eq(SystemAuditOperation.REFERENCE_DATA_DELETE), any(java.util.Map.class),
            any(SecurityException.class)
        );
    }

    @Test
    void blocksDeletingAGroupCodeUsedByAMember() {
        ReferenceData reference = reference(ReferenceDataType.GROUP_CODE, "YOUTH");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), eq(Member.class))).thenReturn(true);

        Member actor = actor(Role.ADMIN);
        assertThatThrownBy(() -> service().delete(actor, "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("member");
        verify(audit).recordFailure(
            eq(actor), eq(SystemAuditOperation.REFERENCE_DATA_DELETE), any(java.util.Map.class),
            any(DeletionBlockedException.class)
        );
    }

    @Test
    void blocksDeletingAFinancialCategoryWithChildSubcategories() {
        ReferenceData reference = reference(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), any(Class.class)))
            .thenAnswer(invocation -> invocation.getArgument(1).equals(ReferenceData.class));

        assertThatThrownBy(() -> service().delete(actor(Role.ADMIN), "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("sub-categories");
    }

    @Test
    void blocksDeletingAReferenceRequiredByACleanedFiscalArchive() {
        ReferenceData reference = reference(ReferenceDataType.PAYMENT_METHOD, "CASH");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), any(Class.class)))
            .thenAnswer(invocation -> invocation.getArgument(1).equals(FiscalArchiveRegistry.class));

        assertThatThrownBy(() -> service().delete(actor(Role.ADMIN), "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("fiscal archive");
    }

    @Test
    void blocksDeletingMigratedOfferingCategoryRequiredByLegacyCleanedArchive() {
        ReferenceData reference = reference(ReferenceDataType.OFFERING_CATEGORY, "TITHE");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            if (!invocation.getArgument(1).equals(FiscalArchiveRegistry.class)) {
                return false;
            }
            Query query = invocation.getArgument(0);
            return query.getQueryObject().containsKey("fundCategories");
        });

        assertThatThrownBy(() -> service().delete(actor(Role.ADMIN), "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("fiscal archive");
    }

    @Test
    void blocksDeletingCommitteeCodeAssignedToMember() {
        ReferenceData reference = reference(ReferenceDataType.COMMITTEE_CODE, "WORSHIP");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), eq(Member.class))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(actor(Role.ADMIN), "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("member records");
    }

    private ReferenceDataDeletionService service() {
        return new ReferenceDataDeletionService(referenceDataRepository, mongoTemplate, audit);
    }

    private Member actor(Role role) {
        Member member = new Member();
        member.setRoles(Set.of(role));
        return member;
    }

    private ReferenceData reference(ReferenceDataType type, String code) {
        ReferenceData reference = new ReferenceData();
        reference.setId("ref-id");
        reference.setType(type);
        reference.setCode(code);
        return reference;
    }
}
