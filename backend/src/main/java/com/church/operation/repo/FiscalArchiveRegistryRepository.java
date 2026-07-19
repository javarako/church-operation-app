package com.church.operation.repo;

import com.church.operation.entity.FiscalArchiveRegistry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FiscalArchiveRegistryRepository extends MongoRepository<FiscalArchiveRegistry, String> {
    Optional<FiscalArchiveRegistry> findByArchiveId(String archiveId);
}
