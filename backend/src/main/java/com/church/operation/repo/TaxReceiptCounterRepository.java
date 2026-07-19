package com.church.operation.repo;

import com.church.operation.entity.TaxReceiptCounter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxReceiptCounterRepository extends MongoRepository<TaxReceiptCounter, Integer> {
}
