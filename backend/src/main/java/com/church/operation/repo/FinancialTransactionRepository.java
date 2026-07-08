package com.church.operation.repo;

import com.church.operation.entity.FinancialTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FinancialTransactionRepository extends MongoRepository<FinancialTransaction, String> {
    @Query(value = "{ 'deleted' : { $ne : true } }", sort = "{ 'transactionDate' : -1, 'createdAt' : -1 }")
    List<FinancialTransaction> findByDeletedFalseOrderByTransactionDateDescCreatedAtDesc();
}
