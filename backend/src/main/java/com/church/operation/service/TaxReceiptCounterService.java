package com.church.operation.service;

import com.church.operation.entity.TaxReceiptCounter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class TaxReceiptCounterService {
    private final MongoTemplate mongoTemplate;

    public TaxReceiptCounterService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String nextReceiptNumber(int taxYear) {
        TaxReceiptCounter counter = mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").is(taxYear)),
            new Update().inc("nextSequence", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            TaxReceiptCounter.class
        );
        if (counter == null) {
            throw new IllegalStateException("Unable to allocate a tax receipt number.");
        }
        return "%d-%06d".formatted(taxYear, counter.getNextSequence());
    }
}
