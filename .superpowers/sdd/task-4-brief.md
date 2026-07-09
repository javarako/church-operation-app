### Task 4: Add Budget REST API

**Files:**
- Create: `backend/src/main/java/com/church/operation/rest/BudgetController.java`

**Interfaces:**
- Produces `GET /api/budgets?fiscalYear=2026`.
- Produces `POST /api/budgets`.
- Produces `PUT /api/budgets/{id}`.
- Produces `DELETE /api/budgets/{id}`.

- [ ] **Step 1: Add controller**

Create `BudgetController`:

```java
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
```

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

---

