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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class FinancialTransactionService {
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ReferenceDataRepository referenceDataRepository;

    public FinancialTransactionService(
        FinancialTransactionRepository financialTransactionRepository,
        ReferenceDataRepository referenceDataRepository
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.referenceDataRepository = referenceDataRepository;
    }

    public List<FinancialTransaction> listTransactions(Member actor) {
        requireFinanceAccess(actor, false);
        return financialTransactionRepository.findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc();
    }

    public FinancialTransaction createExpense(Member actor, FinancialTransactionRequest request) {
        requireFinanceAccess(actor, true);
        validateRequest(request);
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setType(FinancialTransactionType.EXPENSE);
        transaction.setSourceType(FinancialSourceType.MANUAL);
        transaction.setCreatedBy(actor.getId());
        transaction.setCreatedAt(Instant.now());
        applyExpenseFields(transaction, request);
        return financialTransactionRepository.save(transaction);
    }

    public FinancialTransaction updateExpense(Member actor, String id, FinancialTransactionRequest request) {
        requireFinanceAccess(actor, true);
        validateRequest(request);
        FinancialTransaction transaction = findEditableExpense(id, "Offering-generated income cannot be edited from Finance.");
        applyExpenseFields(transaction, request);
        return financialTransactionRepository.save(transaction);
    }

    public FinancialTransaction deleteExpense(Member actor, String id) {
        requireFinanceAccess(actor, true);
        FinancialTransaction transaction = findEditableExpense(id, "Offering-generated income cannot be deleted from Finance.");
        transaction.setDeleted(true);
        transaction.setDeletedBy(actor.getId());
        transaction.setDeletedAt(Instant.now());
        return financialTransactionRepository.save(transaction);
    }

    private FinancialTransaction findEditableExpense(String id, String offeringMessage) {
        FinancialTransaction transaction = financialTransactionRepository.findById(id)
            .filter(existing -> !existing.isDeleted())
            .orElseThrow(() -> new IllegalArgumentException("Expense transaction was not found."));
        if (transaction.getSourceType() == FinancialSourceType.OFFERING) {
            throw new IllegalArgumentException(offeringMessage);
        }
        if (transaction.getSourceType() != FinancialSourceType.MANUAL || transaction.getType() != FinancialTransactionType.EXPENSE) {
            throw new IllegalArgumentException("Expense transaction was not found.");
        }
        return transaction;
    }

    private void applyExpenseFields(FinancialTransaction transaction, FinancialTransactionRequest request) {
        transaction.setTransactionDate(request.transactionDate());
        transaction.setAmount(request.amount());
        transaction.setCategory(normalizeCategory(request.category()));
        transaction.setSubCategory(normalizeSubCategory(request.category(), request.subCategory()));
        transaction.setHstIncluded(request.hstIncluded());
        transaction.setChequeNo(trimToNull(request.chequeNo()));
        transaction.setChequeCleared(request.chequeCleared());
        transaction.setPayableTo(trimToNull(request.payableTo()));
        transaction.setTreasurer(trimToNull(request.treasurer()));
        transaction.setMemo(trimToNull(request.memo()));
    }

    private void validateRequest(FinancialTransactionRequest request) {
        if (request.transactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        if (trimToNull(request.category()) == null) {
            throw new IllegalArgumentException("Financial category is required.");
        }
    }

    private String normalizeCategory(String category) {
        String normalized = trimToNull(category).toUpperCase(Locale.ROOT);
        referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_CATEGORY, normalized)
            .filter(ReferenceData::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Financial category was not found."));
        return normalized;
    }

    private String normalizeSubCategory(String category, String subCategory) {
        String normalized = trimToNull(subCategory);
        if (normalized == null) {
            return null;
        }
        String normalizedCategory = trimToNull(category).toUpperCase(Locale.ROOT);
        normalized = normalized.toUpperCase(Locale.ROOT);
        final String selectedSubCategory = normalized;
        referenceDataRepository.findByTypeAndCode(ReferenceDataType.FINANCIAL_SUB_CATEGORY, normalized)
            .filter(ReferenceData::isActive)
            .filter(referenceData -> normalizedCategory.equals(referenceData.getParentCode()))
            .orElseThrow(() -> new IllegalArgumentException("Financial sub-category was not found for the selected category."));
        return selectedSubCategory;
    }

    private void requireFinanceAccess(Member actor, boolean write) {
        if (hasRole(actor, Role.ADMIN) || hasRole(actor, Role.TREASURER)) {
            return;
        }
        if (!write && hasRole(actor, Role.VIEWER)) {
            return;
        }
        throw new SecurityException("You do not have permission to manage finance transactions.");
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
