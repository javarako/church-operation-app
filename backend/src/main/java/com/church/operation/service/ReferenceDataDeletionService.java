package com.church.operation.service;

import com.church.operation.entity.Budget;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.entity.FiscalArchiveRegistry;
import com.church.operation.exception.DeletionBlockedException;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import com.church.operation.util.FiscalArchiveStatus;
import com.church.operation.util.SystemAuditOperation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReferenceDataDeletionService {
    private final ReferenceDataRepository referenceDataRepository;
    private final MongoTemplate mongoTemplate;
    private final SystemAuditService audit;

    public ReferenceDataDeletionService(
        ReferenceDataRepository referenceDataRepository,
        MongoTemplate mongoTemplate,
        SystemAuditService audit
    ) {
        this.referenceDataRepository = referenceDataRepository;
        this.mongoTemplate = mongoTemplate;
        this.audit = audit;
    }

    public void delete(Member actor, String id) {
        Map<String, ?> metadata = id == null ? Map.of() : Map.of("referenceDataId", id);
        try {
            deleteReferenceData(actor, id);
            audit.recordSuccess(actor, SystemAuditOperation.REFERENCE_DATA_DELETE, metadata);
        } catch (RuntimeException | Error exception) {
            audit.recordFailure(actor, SystemAuditOperation.REFERENCE_DATA_DELETE, metadata, exception);
            throw exception;
        }
    }

    private void deleteReferenceData(Member actor, String id) {
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
            case COMMITTEE_CODE -> addIfUsed(dependencies, Member.class, "committeeCodes", code, "member records");
            case OFFERING_FUND -> {
                addIfUsed(dependencies, Offering.class, "fundCode", code, "offering records");
                addIfUsed(dependencies, FinancialTransaction.class, "category", code, "financial transactions");
                addIfUsed(dependencies, Budget.class, "category", code, "budgets");
                addIfUsed(dependencies, ReferenceData.class, "parentCode", code, "offering categories");
            }
            case OFFERING_CATEGORY -> {
                addIfUsed(dependencies, Offering.class, "categoryCode", code, "offering records");
                addIfUsed(dependencies, FinancialTransaction.class, "subCategory", code, "financial transactions");
                addIfUsed(dependencies, Budget.class, "subCategory", code, "budgets");
            }
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
        String archiveField = switch (reference.getType()) {
            case GROUP_CODE -> "groupCodes";
            case MEMBERSHIP_STATUS -> "membershipStatuses";
            case COMMITTEE_CODE -> null;
            case OFFERING_FUND -> "offeringFunds";
            case OFFERING_CATEGORY -> "offeringCategories";
            case OFFERING_FUND_CATEGORY -> "fundCategories";
            case PAYMENT_METHOD -> "paymentMethods";
            case FINANCIAL_CATEGORY -> "categories";
            case FINANCIAL_SUB_CATEGORY -> "subCategories";
        };
        if (archiveField != null) {
            if (archiveUsesReference(archiveField, code)
                || reference.getType() == ReferenceDataType.OFFERING_CATEGORY
                    && archiveUsesReference("fundCategories", code)
                || reference.getType() == ReferenceDataType.OFFERING_FUND
                    && OfferingHierarchyMigrationService.GENERAL_FUND.equals(code)
                    && legacyArchiveContainsOfferingValues()) {
                dependencies.add("a cleaned fiscal archive");
            }
        }
        return dependencies;
    }

    private boolean archiveUsesReference(String field, String code) {
        Query query = Query.query(Criteria.where(field).is(code)
            .and("status").is(FiscalArchiveStatus.CLEANED));
        return mongoTemplate.exists(query, FiscalArchiveRegistry.class);
    }

    private boolean legacyArchiveContainsOfferingValues() {
        Query query = Query.query(Criteria.where("fundCategories.0").exists(true)
            .and("status").is(FiscalArchiveStatus.CLEANED));
        return mongoTemplate.exists(query, FiscalArchiveRegistry.class);
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
        if (!hasRole(actor, Role.ADMIN)) {
            throw new SecurityException("You do not have permission to delete reference data.");
        }
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }
}
