package com.church.operation.rest;

import com.church.operation.dto.BudgetRequest;
import com.church.operation.dto.BudgetResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.BudgetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    List<BudgetResponse> listBudgets(Authentication authentication, @RequestParam("fiscalYear") int fiscalYear) {
        return budgetService.listBudgets(actor(authentication), fiscalYear).stream()
            .map(BudgetResponse::from)
            .toList();
    }

    @PostMapping
    BudgetResponse createBudget(Authentication authentication, @RequestBody BudgetRequest request) {
        return BudgetResponse.from(budgetService.createBudget(actor(authentication), request));
    }

    @PutMapping("/{id}")
    BudgetResponse updateBudget(Authentication authentication, @PathVariable("id") String id, @RequestBody BudgetRequest request) {
        return BudgetResponse.from(budgetService.updateBudget(actor(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    BudgetResponse deleteBudget(Authentication authentication, @PathVariable("id") String id) {
        return BudgetResponse.from(budgetService.deleteBudget(actor(authentication), id));
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
