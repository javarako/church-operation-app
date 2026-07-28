package com.church.operation.repo;

import com.church.operation.entity.EmailSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailSettingsRepository extends MongoRepository<EmailSettings, String> {
}
