package com.church.operation.repo;

import com.church.operation.entity.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {
    Optional<Member> findByPrimaryEmail(String primaryEmail);
    Optional<Member> findByOfferingNumber(String offeringNumber);
    boolean existsByPrimaryEmail(String primaryEmail);
    boolean existsByOfferingNumber(String offeringNumber);
}
