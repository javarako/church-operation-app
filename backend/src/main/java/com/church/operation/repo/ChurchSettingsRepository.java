package com.church.operation.repo;

import com.church.operation.entity.ChurchSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChurchSettingsRepository extends MongoRepository<ChurchSettings, String> {
}
