package com.church.operation.service;

import com.church.operation.dto.FinancialTransactionRequest;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void treasurerCreatesManualExpense() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransactionRequest request = expenseRequest("OFFICE", "SUPPLIES");
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE", null)));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES", "OFFICE")));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialTransaction saved = service().createExpense(treasurer, request);

        assertThat(saved.getType()).isEqualTo(FinancialTransactionType.EXPENSE);
        assertThat(saved.getSourceType()).isEqualTo(FinancialSourceType.MANUAL);
        assertThat(saved.getCategory()).isEqualTo("OFFICE");
        assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
        assertThat(saved.getAmount()).isEqualByComparingTo("25.50");
        assertThat(saved.getCreatedBy()).isEqualTo("treasurer-id");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void viewerCanListTransactionsReadOnly() {
        Member viewer = member("viewer-id", Role.VIEWER);
        FinancialTransaction income = incomeTransaction();
        when(financialTransactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc())
            .thenReturn(List.of(income));

        List<FinancialTransaction> transactions = service().listTransactions(viewer);

        assertThat(transactions).containsExactly(income);
    }

    @Test
    void hidesCancelledOfferingIncomeFromFinanceList() {
        Member viewer = member("viewer-id", Role.VIEWER);
        FinancialTransaction cancelledIncome = incomeTransaction();
        cancelledIncome.setId("cancelled-income");
        cancelledIncome.setAmount(BigDecimal.ZERO);
        FinancialTransaction expense = manualExpense("expense-1");
        when(financialTransactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc())
            .thenReturn(List.of(cancelledIncome, expense));

        List<FinancialTransaction> transactions = service().listTransactions(viewer);

        assertThat(transactions).containsExactly(expense);
    }

    @Test
    void rejectsViewerCreatingExpense() {
        Member viewer = member("viewer-id", Role.VIEWER);

        assertThatThrownBy(() -> service().createExpense(viewer, expenseRequest("OFFICE", "SUPPLIES")))
            .isInstanceOf(SecurityException.class)
            .hasMessage("You do not have permission to manage finance transactions.");
    }

    @Test
    void rejectsMissingTransactionDate() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransactionRequest request = new FinancialTransactionRequest(
            null,
            new BigDecimal("25.50"),
            "OFFICE",
            "SUPPLIES",
            true,
            "1001",
            false,
            "Office Depot",
            "Brad Ko",
            "Paper"
        );

        assertThatThrownBy(() -> service().createExpense(treasurer, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Transaction date is required.");
    }

    @Test
    void rejectsUnknownCategory() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createExpense(treasurer, expenseRequest("OFFICE", "SUPPLIES")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Financial category was not found.");
    }

    @Test
    void rejectsExpenseSubCategoryOutsideSelectedCategory() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE", null)));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "UTILITIES"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "UTILITIES", "FACILITY")));

        assertThatThrownBy(() -> service().createExpense(treasurer, expenseRequest("OFFICE", "UTILITIES")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Financial sub-category was not found for the selected category.");
    }

    @Test
    void updatesManualExpense() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransaction existing = manualExpense("expense-1");
        when(financialTransactionRepository.findById("expense-1")).thenReturn(Optional.of(existing));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_CATEGORY, "OFFICE", null)));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.FINANCIAL_SUB_CATEGORY, "SUPPLIES", "OFFICE")));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialTransaction saved = service().updateExpense(treasurer, "expense-1", expenseRequest("OFFICE", "SUPPLIES"));

        assertThat(saved.getCategory()).isEqualTo("OFFICE");
        assertThat(saved.getSubCategory()).isEqualTo("SUPPLIES");
        assertThat(saved.getSourceType()).isEqualTo(FinancialSourceType.MANUAL);
        assertThat(saved.getPayableTo()).isEqualTo("Office Depot");
    }

    @Test
    void softDeletesManualExpense() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransaction existing = manualExpense("expense-1");
        when(financialTransactionRepository.findById("expense-1")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialTransaction deleted = service().deleteExpense(treasurer, "expense-1");

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedBy()).isEqualTo("treasurer-id");
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void rejectsUpdateForOfferingGeneratedIncome() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransaction income = incomeTransaction();
        when(financialTransactionRepository.findById(income.getId())).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> service().updateExpense(treasurer, income.getId(), expenseRequest("OFFICE", "SUPPLIES")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Offering-generated income cannot be edited from Finance.");
    }

    @Test
    void rejectsDeleteForOfferingGeneratedIncome() {
        Member treasurer = member("treasurer-id", Role.TREASURER);
        FinancialTransaction income = incomeTransaction();
        when(financialTransactionRepository.findById(income.getId())).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> service().deleteExpense(treasurer, income.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Offering-generated income cannot be deleted from Finance.");
    }

    private FinancialTransactionService service() {
        return new FinancialTransactionService(financialTransactionRepository, referenceDataRepository);
    }

    private FinancialTransactionRequest expenseRequest(String category, String subCategory) {
        return new FinancialTransactionRequest(
            LocalDate.of(2026, 7, 8),
            new BigDecimal("25.50"),
            category,
            subCategory,
            true,
            "1001",
            false,
            "Office Depot",
            "Brad Ko",
            "Paper supplies"
        );
    }

    private FinancialTransaction manualExpense(String id) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(id);
        transaction.setType(FinancialTransactionType.EXPENSE);
        transaction.setSourceType(FinancialSourceType.MANUAL);
        transaction.setTransactionDate(LocalDate.of(2026, 7, 1));
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setCategory("OFFICE");
        return transaction;
    }

    private FinancialTransaction incomeTransaction() {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId("income-1");
        transaction.setType(FinancialTransactionType.INCOME);
        transaction.setSourceType(FinancialSourceType.OFFERING);
        transaction.setSourceId("offering-1");
        transaction.setTransactionDate(LocalDate.of(2026, 7, 8));
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCategory("TITHE");
        return transaction;
    }

    private Member member(String id, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(id + "@example.com");
        member.setRoles(Set.of(role));
        member.setActive(true);
        return member;
    }

    private ReferenceData activeReference(ReferenceDataType type, String code, String parentCode) {
        ReferenceData referenceData = new ReferenceData();
        referenceData.setType(type);
        referenceData.setCode(code);
        referenceData.setLabel(code);
        referenceData.setParentCode(parentCode);
        referenceData.setActive(true);
        return referenceData;
    }
}
