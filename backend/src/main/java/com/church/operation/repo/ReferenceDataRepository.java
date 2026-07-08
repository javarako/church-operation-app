package com.church.operation.repo;

import com.church.operation.entity.ReferenceData;
import com.church.operation.util.ReferenceDataType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceDataRepository extends MongoRepository<ReferenceData, String> {
    boolean existsByTypeAndCode(ReferenceDataType type, String code);
    List<ReferenceData> findByTypeOrderBySortOrderAscLabelAsc(ReferenceDataType type);
    List<ReferenceData> findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(ReferenceDataType type);
    List<ReferenceData> findByTypeAndParentCodeAndActiveTrueOrderBySortOrderAscLabelAsc(ReferenceDataType type, String parentCode);
    Optional<ReferenceData> findByTypeAndCode(ReferenceDataType type, String code);
}
