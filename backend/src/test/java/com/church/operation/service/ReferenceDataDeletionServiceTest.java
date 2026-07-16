package com.church.operation.service;

import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
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

    @Test
    void deletesUnusedReferenceData() {
        Member actor = actor(Role.TREASURER);
        ReferenceData reference = reference(ReferenceDataType.PAYMENT_METHOD, "CASH");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));

        service().delete(actor, "ref-id");

        verify(referenceDataRepository).delete(reference);
    }

    @Test
    void blocksDeletingAGroupCodeUsedByAMember() {
        ReferenceData reference = reference(ReferenceDataType.GROUP_CODE, "YOUTH");
        when(referenceDataRepository.findById("ref-id")).thenReturn(Optional.of(reference));
        when(mongoTemplate.exists(any(Query.class), eq(Member.class))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(actor(Role.MEMBERSHIP), "ref-id"))
            .isInstanceOf(DeletionBlockedException.class)
            .hasMessageContaining("member");
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

    private ReferenceDataDeletionService service() {
        return new ReferenceDataDeletionService(referenceDataRepository, mongoTemplate);
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
