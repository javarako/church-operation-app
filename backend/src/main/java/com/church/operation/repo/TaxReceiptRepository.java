package com.church.operation.repo;

import com.church.operation.entity.TaxReceipt;
import com.church.operation.util.TaxReceiptStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TaxReceiptRepository extends MongoRepository<TaxReceipt, String> {
    Optional<TaxReceipt> findByReceiptNumber(String receiptNumber);
    Optional<TaxReceipt> findFirstByTaxYearAndOfferingNumberAndStatusOrderByCreatedAtDesc(
        int taxYear,
        String offeringNumber,
        TaxReceiptStatus status
    );
    Optional<TaxReceipt> findFirstByTaxYearAndOfferingNumberOrderByCreatedAtDesc(int taxYear, String offeringNumber);
    List<TaxReceipt> findByTaxYearOrderByOfferingNumberAsc(int taxYear);
}
