package com.church.operation.repo;

import com.church.operation.entity.Offering;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface OfferingRepository extends MongoRepository<Offering, String> {
    List<Offering> findByDeletedFalseOrderByOfferingDateDescCreatedAtDesc();

    List<Offering> findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
        LocalDate start,
        LocalDate end
    );

    List<Offering> findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
        LocalDate start,
        LocalDate end
    );
}
