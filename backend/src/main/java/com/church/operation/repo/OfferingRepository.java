package com.church.operation.repo;

import com.church.operation.entity.Offering;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OfferingRepository extends MongoRepository<Offering, String> {
    List<Offering> findByDeletedFalseOrderByOfferingDateDescCreatedAtDesc();
}
