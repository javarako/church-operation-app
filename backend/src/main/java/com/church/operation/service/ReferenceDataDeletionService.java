package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReferenceDataDeletionService {
    private final ReferenceDataRepository referenceDataRepository;
    private final MongoTemplate mongoTemplate;

    public ReferenceDataDeletionService(ReferenceDataRepository referenceDataRepository, MongoTemplate mongoTemplate) {
        this.referenceDataRepository = referenceDataRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void delete(Member actor, String id) {
        requireReferenceDataManager(actor);
        ReferenceData reference = referenceDataRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reference data was not found."));

        List<String> dependencies = dependencies(reference);
        if (!dependencies.isEmpty()) {
            throw new DeletionBlockedException(
                "This reference value cannot be deleted because it is used by "
                    + String.join(", ", dependencies) + "."
            );
        }
        referenceDataRepository.delete(reference);
    }

    private List<String> dependencies(ReferenceData reference) {
        String code = reference.getCode();
        List<String> dependencies = new ArrayList<>();
        switch (reference.getType()) {
            case GROUP_CODE -> addIfUsed(dependencies, Member.class, "groupCode", code, "member records");
            case MEMBERSHIP_STATUS -> addIfUsed(dependencies, Member.class, "membershipStatus", code, "member records");
            case OFFERING_FUND_CATEGORY -> {
                addIfUsed(dependencies, Offering.class, "fundCategory", code, "offering records");
                addIfUsed(dependencies, Budget.class, "category", code, "budgets");
            }
            case PAYMENT_METHOD -> addIfUsed(dependencies, Offering.class, "paymentMethod", code, "offering records");
            case FINANCIAL_CATEGORY -> {
                addIfUsed(dependencies, FinancialTransaction.class, "category", code, "financial transactions");
                addIfUsed(dependencies, Budget.class, "category", code, "budgets");
                addIfUsed(dependencies, ReferenceData.class, "parentCode", code, "financial sub-categories");
            }
            case FINANCIAL_SUB_CATEGORY -> {
                addIfUsed(dependencies, FinancialTransaction.class, "subCategory", code, "financial transactions");
                addIfUsed(dependencies, Budget.class, "subCategory", code, "budgets");
            }
        }
        return dependencies;
    }

    private void addIfUsed(
        List<String> dependencies,
        Class<?> entityType,
        String field,
        String code,
        String label
    ) {
        if (mongoTemplate.exists(Query.query(Criteria.where(field).is(code)), entityType)) {
            dependencies.add(label);
        }
    }

    private void requireReferenceDataManager(Member actor) {
        if (!hasRole(actor, Role.ADMIN) && !hasRole(actor, Role.MEMBERSHIP) && !hasRole(actor, Role.TREASURER)) {
            throw new SecurityException("You do not have permission to delete reference data.");
        }
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }
}
