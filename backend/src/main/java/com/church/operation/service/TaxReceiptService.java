package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.TaxReceiptSummaryRow;
import com.church.operation.dto.TaxReceiptValidationError;
import com.church.operation.entity.Address;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.exception.ReceiptValidationException;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.TaxReceiptRepository;
import com.church.operation.util.GivingType;
import com.church.operation.util.Role;
import com.church.operation.util.TaxReceiptStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TaxReceiptService {
    private final OfferingRepository offeringRepository;
    private final MemberRepository memberRepository;
    private final TaxReceiptRepository receiptRepository;
    private final TaxReceiptCounterService counterService;
    private final ChurchInformationProperties churchProperties;
    private final Clock clock;

    @Autowired
    public TaxReceiptService(
        OfferingRepository offeringRepository,
        MemberRepository memberRepository,
        TaxReceiptRepository receiptRepository,
        TaxReceiptCounterService counterService,
        ChurchInformationProperties churchProperties
    ) {
        this(offeringRepository, memberRepository, receiptRepository, counterService, churchProperties, Clock.systemDefaultZone());
    }

    TaxReceiptService(
        OfferingRepository offeringRepository,
        MemberRepository memberRepository,
        TaxReceiptRepository receiptRepository,
        TaxReceiptCounterService counterService,
        ChurchInformationProperties churchProperties,
        Clock clock
    ) {
        this.offeringRepository = offeringRepository;
        this.memberRepository = memberRepository;
        this.receiptRepository = receiptRepository;
        this.counterService = counterService;
        this.churchProperties = churchProperties;
        this.clock = clock;
    }

    public List<TaxReceiptSummaryRow> summary(Member actor, int taxYear, String offeringNumber) {
        requireTaxAccess(actor);
        validateYear(taxYear);
        Map<String, Member> membersById = memberRepository.findAll().stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<Offering>> offeringsByMember = eligibleOfferings(taxYear).stream()
            .filter(offering -> membersById.containsKey(offering.getMemberId()))
            .collect(Collectors.groupingBy(Offering::getMemberId, LinkedHashMap::new, Collectors.toList()));

        return offeringsByMember.entrySet().stream()
            .map(entry -> toSummary(taxYear, membersById.get(entry.getKey()), entry.getValue()))
            .filter(row -> isBlank(offeringNumber) || Objects.equals(row.offeringNumber(), offeringNumber))
            .sorted(Comparator.comparing(TaxReceiptSummaryRow::offeringNumber, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public TaxReceipt issue(Member actor, int taxYear, String offeringNumber, String thankYouNote) {
        requireTaxAccess(actor);
        validateYear(taxYear);
        if (isBlank(offeringNumber)) {
            throw validation(null, null, "offering number is required");
        }
        Optional<TaxReceipt> existing = activeReceipt(taxYear, offeringNumber);
        if (existing.isPresent()) {
            return existing.get();
        }

        Member member = memberRepository.findByOfferingNumber(offeringNumber)
            .orElseThrow(() -> validation(offeringNumber, null, "member record is required"));
        List<Offering> offerings = eligibleOfferings(taxYear).stream()
            .filter(offering -> Objects.equals(offering.getMemberId(), member.getId()))
            .toList();
        validate(member, offerings, thankYouNote);
        return receiptRepository.save(createSnapshot(actor, taxYear, member, offerings, thankYouNote, null));
    }

    public List<TaxReceipt> issueBatch(Member actor, int taxYear, String thankYouNote) {
        requireTaxAccess(actor);
        List<TaxReceiptSummaryRow> rows = summary(actor, taxYear, null);
        List<TaxReceiptValidationError> errors = new ArrayList<>();
        Map<String, Member> members = memberRepository.findAll().stream()
            .collect(Collectors.toMap(Member::getOfferingNumber, member -> member, (left, right) -> left));
        List<Offering> allOfferings = eligibleOfferings(taxYear);
        for (TaxReceiptSummaryRow row : rows) {
            if (row.receiptStatus() == TaxReceiptStatus.ISSUED) {
                continue;
            }
            Member member = members.get(row.offeringNumber());
            List<Offering> offerings = allOfferings.stream()
                .filter(offering -> member != null && Objects.equals(offering.getMemberId(), member.getId()))
                .toList();
            errors.addAll(validationErrors(member, offerings, thankYouNote, row.offeringNumber()));
        }
        if (!errors.isEmpty()) {
            throw new ReceiptValidationException(errors);
        }
        return rows.stream()
            .map(row -> issue(actor, taxYear, row.offeringNumber(), thankYouNote))
            .toList();
    }

    public TaxReceipt findById(Member actor, String receiptId) {
        requireTaxAccess(actor);
        return receiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Tax receipt was not found."));
    }

    public TaxReceipt voidReceipt(Member actor, String receiptId, String reason) {
        requireTaxAccess(actor);
        if (isBlank(reason)) {
            throw new IllegalArgumentException("A void reason is required.");
        }
        TaxReceipt receipt = findById(actor, receiptId);
        if (receipt.getStatus() == TaxReceiptStatus.VOID) {
            return receipt;
        }
        receipt.setStatus(TaxReceiptStatus.VOID);
        receipt.setVoidReason(reason.trim());
        receipt.setVoidedAt(Instant.now(clock));
        receipt.setVoidedByMemberId(actor.getId());
        receipt.setUpdatedAt(Instant.now(clock));
        return receiptRepository.save(receipt);
    }

    public TaxReceipt replaceReceipt(Member actor, String receiptId, String thankYouNote) {
        requireTaxAccess(actor);
        TaxReceipt original = findById(actor, receiptId);
        if (original.getStatus() != TaxReceiptStatus.VOID) {
            throw new IllegalArgumentException("Only a void receipt can be replaced.");
        }
        if (!isBlank(original.getReplacementReceiptId())) {
            return findById(actor, original.getReplacementReceiptId());
        }
        Member member = memberRepository.findByOfferingNumber(original.getOfferingNumber())
            .orElseThrow(() -> validation(original.getOfferingNumber(), original.getDonorName(), "member record is required"));
        List<Offering> offerings = eligibleOfferings(original.getTaxYear()).stream()
            .filter(offering -> Objects.equals(offering.getMemberId(), member.getId()))
            .toList();
        validate(member, offerings, thankYouNote);
        TaxReceipt replacement = receiptRepository.save(createSnapshot(
            actor, original.getTaxYear(), member, offerings, thankYouNote, original.getId()
        ));
        original.setReplacementReceiptId(replacement.getId());
        original.setUpdatedAt(Instant.now(clock));
        receiptRepository.save(original);
        return replacement;
    }

    private TaxReceiptSummaryRow toSummary(int taxYear, Member member, List<Offering> offerings) {
        BigDecimal total = total(offerings);
        Optional<TaxReceipt> receipt = latestReceipt(taxYear, member.getOfferingNumber());
        boolean sourceChanged = receipt.map(value -> !Objects.equals(value.getSourceChecksum(), checksum(offerings))).orElse(false);
        return new TaxReceiptSummaryRow(
            member.getId(), member.getOfferingNumber(), member.getDisplayName(), formatAddress(member.getMailingAddress()),
            taxYear, total, receipt.map(TaxReceipt::getId).orElse(null),
            receipt.map(TaxReceipt::getReceiptNumber).orElse(null), receipt.map(TaxReceipt::getStatus).orElse(null), sourceChanged
        );
    }

    private TaxReceipt createSnapshot(
        Member actor,
        int taxYear,
        Member member,
        List<Offering> offerings,
        String thankYouNote,
        String replacesReceiptId
    ) {
        Instant now = Instant.now(clock);
        ChurchInformationProperties.Information church = churchProperties.information();
        List<Offering> sorted = offerings.stream().sorted(Comparator.comparing(Offering::getId)).toList();
        BigDecimal total = total(sorted);
        TaxReceipt receipt = new TaxReceipt();
        receipt.setReceiptNumber(counterService.nextReceiptNumber(taxYear));
        receipt.setStatus(TaxReceiptStatus.ISSUED);
        receipt.setTaxYear(taxYear);
        receipt.setIssueDate(LocalDate.now(clock));
        receipt.setIssuedByMemberId(actor.getId());
        receipt.setMemberId(member.getId());
        receipt.setOfferingNumber(member.getOfferingNumber());
        receipt.setDonorName(member.getDisplayName().trim());
        receipt.setDonorAddress(formatAddress(member.getMailingAddress()));
        receipt.setDonorEmail(member.getPrimaryEmail());
        receipt.setChurchName(church.name());
        receipt.setChurchAddress(church.address());
        receipt.setCharityRegistrationNumber(church.charityRegistrationNumber());
        receipt.setChurchWebsite(church.website());
        receipt.setReceiptIssueLocation(church.receiptIssueLocation());
        receipt.setTreasurerName(church.treasurerName());
        receipt.setGiftAmount(total);
        receipt.setEligibleAmount(total);
        receipt.setAdvantageAmount(BigDecimal.ZERO.setScale(2));
        receipt.setAdvantageDescription("None");
        receipt.setThankYouNote(thankYouNote == null ? "" : thankYouNote.trim());
        receipt.setSourceOfferingIds(sorted.stream().map(Offering::getId).toList());
        receipt.setSourceChecksum(checksum(sorted));
        receipt.setReplacesReceiptId(replacesReceiptId);
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);
        return receipt;
    }

    private void validate(Member member, List<Offering> offerings, String note) {
        List<TaxReceiptValidationError> errors = validationErrors(
            member, offerings, note, member != null ? member.getOfferingNumber() : null
        );
        if (!errors.isEmpty()) {
            throw new ReceiptValidationException(errors);
        }
    }

    private List<TaxReceiptValidationError> validationErrors(
        Member member,
        List<Offering> offerings,
        String note,
        String offeringNumber
    ) {
        List<String> errors = new ArrayList<>();
        if (member == null) {
            errors.add("member record is required");
        } else {
            if (isBlank(member.getDisplayName())) errors.add("donor full name is required");
            if (isBlank(member.getOfferingNumber())) errors.add("offering number is required");
            if (!completeAddress(member.getMailingAddress())) errors.add("donor address is incomplete");
        }
        if (offerings == null || offerings.isEmpty()) errors.add("eligible offerings are required");
        if (note != null && note.length() > 500) errors.add("thank-you note must be 500 characters or fewer");
        ChurchInformationProperties.Information church = churchProperties.information();
        if (Stream.of(church.name(), church.address(), church.charityRegistrationNumber(), church.website(),
            church.receiptIssueLocation(), church.treasurerName()).anyMatch(this::isBlank)) {
            errors.add("church receipt configuration is incomplete");
        }
        return errors.isEmpty() ? List.of() : List.of(new TaxReceiptValidationError(
            offeringNumber, member != null ? member.getDisplayName() : null, List.copyOf(errors)
        ));
    }

    private List<Offering> eligibleOfferings(int taxYear) {
        return offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(taxYear, 1, 1), LocalDate.of(taxYear, 12, 31)
        ).stream()
            .filter(offering -> offering.getGivingType() == GivingType.MEMBER)
            .filter(offering -> offering.getAmount() != null && offering.getAmount().compareTo(BigDecimal.ZERO) > 0)
            .toList();
    }

    private Optional<TaxReceipt> activeReceipt(int taxYear, String offeringNumber) {
        if (isBlank(offeringNumber)) return Optional.empty();
        return receiptRepository.findFirstByTaxYearAndOfferingNumberAndStatusOrderByCreatedAtDesc(
            taxYear, offeringNumber, TaxReceiptStatus.ISSUED
        );
    }

    private Optional<TaxReceipt> latestReceipt(int taxYear, String offeringNumber) {
        if (isBlank(offeringNumber)) return Optional.empty();
        return receiptRepository.findFirstByTaxYearAndOfferingNumberOrderByCreatedAtDesc(taxYear, offeringNumber);
    }

    private BigDecimal total(List<Offering> offerings) {
        return offerings.stream().map(Offering::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String checksum(List<Offering> offerings) {
        String canonical = offerings.stream()
            .sorted(Comparator.comparing(Offering::getId))
            .map(offering -> offering.getId() + ":" + offering.getAmount().toPlainString())
            .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private boolean completeAddress(Address address) {
        return address != null && Stream.of(
            address.addressLine1(), address.city(), address.provinceState(), address.postalZipCode(), address.country()
        ).noneMatch(this::isBlank);
    }

    private String formatAddress(Address address) {
        if (address == null) return null;
        return Stream.of(address.addressLine1(), address.addressLine2(), address.city(), address.provinceState(),
                address.postalZipCode(), address.country())
            .filter(value -> !isBlank(value))
            .collect(Collectors.joining(", "));
    }

    private void requireTaxAccess(Member actor) {
        if (actor != null && actor.getRoles() != null
            && (actor.getRoles().contains(Role.ADMIN) || actor.getRoles().contains(Role.TREASURER))) {
            return;
        }
        throw new SecurityException("You do not have permission to manage official tax receipts.");
    }

    private void validateYear(int year) {
        if (year < 1900 || year > 2200) throw new IllegalArgumentException("A valid tax year is required.");
    }

    private ReceiptValidationException validation(String offeringNumber, String donorName, String message) {
        return new ReceiptValidationException(List.of(new TaxReceiptValidationError(
            offeringNumber, donorName, List.of(message)
        )));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
