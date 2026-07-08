package com.church.operation.repo;

import com.church.operation.entity.FinancialTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FinancialTransactionRepository extends MongoRepository<FinancialTransaction, String> {
}
