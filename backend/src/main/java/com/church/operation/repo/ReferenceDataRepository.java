package com.church.operation.repo;

import com.church.operation.entity.ReferenceData;
import com.church.operation.util.ReferenceDataType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceDataRepository extends MongoRepository<ReferenceData, String> {
    boolean existsByTypeAndCode(ReferenceDataType type, String code);
    List<ReferenceData> findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(ReferenceDataType type);
    Optional<ReferenceData> findByTypeAndCode(ReferenceDataType type, String code);
}
