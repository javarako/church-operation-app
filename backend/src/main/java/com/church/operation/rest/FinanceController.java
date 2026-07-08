package com.church.operation.rest;

import com.church.operation.dto.FinancialTransactionRequest;
import com.church.operation.dto.FinancialTransactionResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.FinancialTransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {
    private final FinancialTransactionService financialTransactionService;

    public FinanceController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @GetMapping("/transactions")
    List<FinancialTransactionResponse> listTransactions(Authentication authentication) {
        return financialTransactionService.listTransactions(actor(authentication)).stream()
            .map(FinancialTransactionResponse::from)
            .toList();
    }

    @PostMapping("/expenses")
    FinancialTransactionResponse createExpense(Authentication authentication, @RequestBody FinancialTransactionRequest request) {
        return FinancialTransactionResponse.from(financialTransactionService.createExpense(actor(authentication), request));
    }

    @PutMapping("/expenses/{id}")
    FinancialTransactionResponse updateExpense(
        Authentication authentication,
        @PathVariable("id") String id,
        @RequestBody FinancialTransactionRequest request
    ) {
        return FinancialTransactionResponse.from(financialTransactionService.updateExpense(actor(authentication), id, request));
    }

    @DeleteMapping("/expenses/{id}")
    FinancialTransactionResponse deleteExpense(Authentication authentication, @PathVariable("id") String id) {
        return FinancialTransactionResponse.from(financialTransactionService.deleteExpense(actor(authentication), id));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
