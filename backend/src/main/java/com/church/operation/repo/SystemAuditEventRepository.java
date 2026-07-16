package com.church.operation.repo;

import com.church.operation.entity.SystemAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SystemAuditEventRepository extends MongoRepository<SystemAuditEvent, String> {
}
