package com.church.operation.repo;

import com.church.operation.entity.YearEndClosing;
import com.church.operation.util.YearEndReportType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface YearEndClosingRepository extends MongoRepository<YearEndClosing, String> {
    Optional<YearEndClosing> findByFiscalYearAndReportTypeAndActiveTrue(
        int fiscalYear,
        YearEndReportType reportType
    );

    Optional<YearEndClosing> findFirstByFiscalYearAndReportTypeOrderByVersionDesc(
        int fiscalYear,
        YearEndReportType reportType
    );
}
