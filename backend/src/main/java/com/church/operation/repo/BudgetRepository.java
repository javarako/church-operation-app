package com.church.operation.repo;

import com.church.operation.entity.Budget;
import com.church.operation.util.BudgetType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface BudgetRepository extends MongoRepository<Budget, String> {
    @Query(value = "{ 'fiscalYear' : ?0, 'deleted' : { $ne : true } }", sort = "{ 'budgetType' : 1, 'category' : 1, 'subCategory' : 1 }")
    List<Budget> findActiveByFiscalYear(int fiscalYear);

    @Query("{ 'fiscalYear' : ?0, 'budgetType' : ?1, 'category' : ?2, 'subCategory' : ?3, 'deleted' : { $ne : true } }")
    List<Budget> findActiveDuplicates(int fiscalYear, BudgetType budgetType, String category, String subCategory);

    @Query("{ 'fiscalYear' : ?0, 'budgetType' : 'CARRY_OVER', 'deleted' : { $ne : true } }")
    List<Budget> findActiveCarryOver(int fiscalYear);
}
