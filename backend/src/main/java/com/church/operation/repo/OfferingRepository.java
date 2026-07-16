package com.church.operation.repo;

import com.church.operation.entity.Offering;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OfferingRepository extends MongoRepository<Offering, String> {
    List<Offering> findByDeletedFalseOrderByOfferingDateDescCreatedAtDesc();

    @Query(value = "{ 'deleted' : false, 'offeringSunday' : { $gte : ?0, $lte : ?1 } }", sort = "{ 'offeringSunday' : 1, 'fundCategory' : 1, 'paymentMethod' : 1 }")
    List<Offering> findByDeletedFalseAndOfferingSundayBetweenOrderByOfferingSundayAscFundCategoryAscPaymentMethodAsc(
        LocalDate start,
        LocalDate end
    );

    @Query(value = "{ 'deleted' : false, 'offeringDate' : { $gte : ?0, $lte : ?1 } }", sort = "{ 'offeringDate' : 1, 'createdAt' : 1 }")
    List<Offering> findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
        LocalDate start,
        LocalDate end
    );

    @Query(value = "{ 'offeringDate' : { $gte : ?0, $lte : ?1 } }", sort = "{ 'offeringDate' : 1, '_id' : 1 }")
    List<Offering> findAllByOfferingDateBetween(LocalDate start, LocalDate end);
}
