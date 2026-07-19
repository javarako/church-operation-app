package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.BudgetRepository;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.BudgetType;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.ReferenceDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingHierarchyMigrationServiceTest {
    @Mock ReferenceDataRepository references;
    @Mock OfferingRepository offerings;
    @Mock FinancialTransactionRepository transactions;
    @Mock BudgetRepository budgets;

    @Test
    void migratesLegacyHierarchyAndCanRunAgainSafely() {
        ReferenceData general = reference(ReferenceDataType.OFFERING_FUND, "GENERAL", null);
        ReferenceData legacy = reference(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE", null);
        Offering offering = new Offering();
        offering.setId("offering-1");
        offering.setFundCategory("TITHE");
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setSourceType(FinancialSourceType.OFFERING);
        transaction.setSourceId("offering-1");
        transaction.setCategory("TITHE");
        Budget budget = new Budget();
        budget.setBudgetType(BudgetType.OFFERING_INCOME);
        budget.setCategory("TITHE");

        when(references.findByTypeAndCode(ReferenceDataType.OFFERING_FUND, "GENERAL"))
            .thenReturn(Optional.of(general));
        when(references.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND_CATEGORY))
            .thenReturn(List.of(legacy));
        when(references.findByTypeAndCode(ReferenceDataType.OFFERING_CATEGORY, "TITHE"))
            .thenReturn(Optional.empty(), Optional.of(reference(ReferenceDataType.OFFERING_CATEGORY, "TITHE", "GENERAL")));
        when(references.save(any(ReferenceData.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerings.findAll()).thenReturn(List.of(offering));
        when(transactions.findAll()).thenReturn(List.of(transaction));
        when(budgets.findAll()).thenReturn(List.of(budget));

        OfferingHierarchyMigrationService service =
            new OfferingHierarchyMigrationService(references, offerings, transactions, budgets);

        service.migrate();
        service.migrate();

        assertThat(offering.getFundCode()).isEqualTo("GENERAL");
        assertThat(offering.getCategoryCode()).isEqualTo("TITHE");
        assertThat(transaction.getCategory()).isEqualTo("GENERAL");
        assertThat(transaction.getSubCategory()).isEqualTo("TITHE");
        assertThat(budget.getCategory()).isEqualTo("GENERAL");
        assertThat(budget.getSubCategory()).isEqualTo("TITHE");
        verify(references, times(1)).save(any(ReferenceData.class));
        verify(offerings, times(1)).save(offering);
        verify(transactions, times(1)).save(transaction);
        verify(budgets, times(1)).save(budget);
    }

    @Test
    void doesNotOverwriteConflictingLinkedIncomeFundDuringPartialMigration() {
        Offering offering = new Offering();
        offering.setId("offering-1");
        offering.setFundCategory("TITHE");
        offering.setFundCode("MISSIONS");
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setSourceType(FinancialSourceType.OFFERING);
        transaction.setSourceId("offering-1");
        transaction.setCategory("MISSIONS");

        when(references.findByTypeAndCode(ReferenceDataType.OFFERING_FUND, "GENERAL"))
            .thenReturn(Optional.of(reference(ReferenceDataType.OFFERING_FUND, "GENERAL", null)));
        when(references.findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType.OFFERING_FUND_CATEGORY))
            .thenReturn(List.of());
        when(offerings.findAll()).thenReturn(List.of(offering));
        when(transactions.findAll()).thenReturn(List.of(transaction));
        when(budgets.findAll()).thenReturn(List.of());

        new OfferingHierarchyMigrationService(references, offerings, transactions, budgets).migrate();

        assertThat(transaction.getCategory()).isEqualTo("MISSIONS");
        assertThat(transaction.getSubCategory()).isNull();
        verify(transactions, never()).save(transaction);
    }

    private ReferenceData reference(ReferenceDataType type, String code, String parentCode) {
        ReferenceData value = new ReferenceData();
        value.setType(type);
        value.setCode(code);
        value.setLabel(code);
        value.setParentCode(parentCode);
        value.setActive(true);
        return value;
    }
}
