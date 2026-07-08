package com.church.operation.service;

import com.church.operation.dto.OfferingRequest;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.GivingType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class OfferingService {
    private final OfferingRepository offeringRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final MemberRepository memberRepository;
    private final ReferenceDataRepository referenceDataRepository;

    public OfferingService(
        OfferingRepository offeringRepository,
        FinancialTransactionRepository financialTransactionRepository,
        MemberRepository memberRepository,
        ReferenceDataRepository referenceDataRepository
    ) {
        this.offeringRepository = offeringRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.memberRepository = memberRepository;
        this.referenceDataRepository = referenceDataRepository;
    }

    public List<Offering> listOfferings(Member actor) {
        requireOfferingAccess(actor, false);
        return offeringRepository.findAllByOrderByOfferingDateDescCreatedAtDesc();
    }

    public Offering createOffering(Member actor, OfferingRequest request) {
        requireOfferingAccess(actor, true);
        validateRequest(request);

        LocalDate offeringSunday = resolveOfferingSunday(request.offeringDate(), request.offeringSunday());
        Instant now = Instant.now();

        Offering offering = new Offering();
        offering.setGivingType(request.givingType());
        offering.setOfferingDate(request.offeringDate());
        offering.setOfferingSunday(offeringSunday);
        offering.setAmount(request.amount());
        offering.setMemo(trimToNull(request.memo()));
        offering.setCreatedBy(actor.getId());
        offering.setCreatedAt(now);
        applyGiver(offering, request);
        offering.setFundCategory(normalizeFundCategory(request.fundCategory()));
        offering.setPaymentMethod(normalizePaymentMethod(request.paymentMethod()));

        Offering savedOffering = offeringRepository.save(offering);
        FinancialTransaction transaction = createIncomeTransaction(savedOffering, actor, now);
        FinancialTransaction savedTransaction = financialTransactionRepository.save(transaction);
        savedOffering.setIncomeTransactionId(savedTransaction.getId());
        return offeringRepository.save(savedOffering);
    }

    private void validateRequest(OfferingRequest request) {
        if (request.givingType() == null) {
            throw new IllegalArgumentException("Giving type is required.");
        }
        if (request.offeringDate() == null) {
            throw new IllegalArgumentException("Offering date is required.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Offering amount must be greater than zero.");
        }
        if (trimToNull(request.fundCategory()) == null) {
            throw new IllegalArgumentException("Offering fund/category is required.");
        }
    }

    private void applyGiver(Offering offering, OfferingRequest request) {
        if (request.givingType() == GivingType.MEMBER) {
            String memberId = trimToNull(request.memberId());
            if (memberId == null) {
                throw new IllegalArgumentException("Member is required for member offering.");
            }
            Member giver = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Offering member was not found."));
            offering.setMemberId(giver.getId());
            offering.setGiverDisplayName(displayName(giver));
            offering.setGiverLabel(null);
            return;
        }

        String giverLabel = trimToNull(request.giverLabel());
        if (giverLabel == null) {
            throw new IllegalArgumentException("Giver label is required for anonymous or group offering.");
        }
        offering.setMemberId(null);
        offering.setGiverLabel(giverLabel);
        offering.setGiverDisplayName(giverLabel);
    }

    private FinancialTransaction createIncomeTransaction(Offering offering, Member actor, Instant createdAt) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setType(FinancialTransactionType.INCOME);
        transaction.setTransactionDate(offering.getOfferingDate());
        transaction.setAmount(offering.getAmount());
        transaction.setCategory(offering.getFundCategory());
        transaction.setSubCategory(null);
        transaction.setSourceType(FinancialSourceType.OFFERING);
        transaction.setSourceId(offering.getId());
        transaction.setMemo(offering.getMemo());
        transaction.setCreatedBy(actor.getId());
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    private String normalizeFundCategory(String fundCategory) {
        String normalized = trimToNull(fundCategory);
        if (normalized == null) {
            throw new IllegalArgumentException("Offering fund/category is required.");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, normalized)
            .filter(referenceData -> referenceData.isActive())
            .orElseThrow(() -> new IllegalArgumentException("Offering fund/category was not found."));
        return normalized;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = trimToNull(paymentMethod);
        if (normalized == null) {
            throw new IllegalArgumentException("Payment method is required.");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, normalized)
            .filter(referenceData -> referenceData.isActive())
            .orElseThrow(() -> new IllegalArgumentException("Payment method was not found."));
        return normalized;
    }

    private LocalDate resolveOfferingSunday(LocalDate offeringDate, LocalDate requestedSunday) {
        if (requestedSunday != null) {
            return requestedSunday;
        }
        int daysUntilSunday = DayOfWeek.SUNDAY.getValue() - offeringDate.getDayOfWeek().getValue();
        if (daysUntilSunday < 0) {
            daysUntilSunday += 7;
        }
        return offeringDate.plusDays(daysUntilSunday);
    }

    private void requireOfferingAccess(Member actor, boolean write) {
        if (hasRole(actor, Role.ADMIN) || hasRole(actor, Role.TREASURER)) {
            return;
        }
        if (!write && hasRole(actor, Role.VIEWER)) {
            return;
        }
        throw new SecurityException("You do not have permission to manage offerings.");
    }

    private boolean hasRole(Member actor, Role role) {
        return actor != null && actor.getRoles() != null && actor.getRoles().contains(role);
    }

    private String displayName(Member member) {
        String displayName = trimToNull(member.getDisplayName());
        return displayName == null ? member.getPrimaryEmail() : displayName;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
