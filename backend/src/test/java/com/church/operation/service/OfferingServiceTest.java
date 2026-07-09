package com.church.operation.service;

import com.church.operation.dto.OfferingRequest;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Member;
import com.church.operation.entity.Offering;
import com.church.operation.entity.ReferenceData;
import com.church.operation.repo.FinancialTransactionRepository;
import com.church.operation.repo.MemberRepository;
import com.church.operation.repo.OfferingRepository;
import com.church.operation.repo.ReferenceDataRepository;
import com.church.operation.util.FinancialSourceType;
import com.church.operation.util.FinancialTransactionType;
import com.church.operation.util.GivingType;
import com.church.operation.util.ReferenceDataType;
import com.church.operation.util.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingServiceTest {
    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private FinancialTransactionRepository financialTransactionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Test
    void createsMemberOfferingAndLinkedIncomeTransaction() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        Member giver = member("member-id", "giver@example.com", Role.MEMBER);
        giver.setDisplayName("Grace Kim");
        OfferingRequest request = request(GivingType.MEMBER, "member-id", null);

        when(memberRepository.findById("member-id")).thenReturn(Optional.of(giver));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, "CASH"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.PAYMENT_METHOD, "CASH")));
        saveOfferingWithId();
        saveTransactionWithId();

        Offering saved = service().createOffering(actor, request);

        assertThat(saved.getId()).isEqualTo("offering-id");
        assertThat(saved.getMemberId()).isEqualTo("member-id");
        assertThat(saved.getGiverDisplayName()).isEqualTo("Grace Kim");
        assertThat(saved.getIncomeTransactionId()).isEqualTo("txn-id");
        verify(financialTransactionRepository).save(argThat(transaction ->
            transaction.getType() == FinancialTransactionType.INCOME
                && transaction.getSourceType() == FinancialSourceType.OFFERING
                && "offering-id".equals(transaction.getSourceId())
                && "TITHE".equals(transaction.getCategory())
        ));
    }

    @Test
    void createsAnonymousOfferingAndLinkedIncomeTransaction() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        OfferingRequest request = request(GivingType.ANONYMOUS, null, "Anonymous");

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, "CASH"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.PAYMENT_METHOD, "CASH")));
        saveOfferingWithId();
        saveTransactionWithId();

        Offering saved = service().createOffering(actor, request);

        assertThat(saved.getGivingType()).isEqualTo(GivingType.ANONYMOUS);
        assertThat(saved.getGiverLabel()).isEqualTo("Anonymous");
        assertThat(saved.getMemberId()).isNull();
        assertThat(saved.getIncomeTransactionId()).isEqualTo("txn-id");
    }

    @Test
    void rejectsMemberOfferingWithoutMemberId() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = request(GivingType.MEMBER, " ", null);

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Member is required for member offering.");
    }

    @Test
    void rejectsAnonymousOfferingWithoutLabel() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = request(GivingType.GROUP, null, " ");

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Giver label is required for anonymous or group offering.");
    }

    @Test
    void rejectsInvalidAmount() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        OfferingRequest request = new OfferingRequest(
            GivingType.ANONYMOUS,
            null,
            "Anonymous",
            LocalDate.of(2026, 7, 8),
            null,
            "TITHE",
            BigDecimal.ZERO,
            "Cash",
            null
        );

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Offering amount must be greater than zero.");
    }

    @Test
    void calculatesComingSundayWhenOfferingSundayIsBlank() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        OfferingRequest request = request(GivingType.ANONYMOUS, null, "Anonymous");

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, "CASH"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.PAYMENT_METHOD, "CASH")));
        saveOfferingWithId();
        saveTransactionWithId();

        Offering saved = service().createOffering(actor, request);

        assertThat(saved.getOfferingDate()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(saved.getOfferingSunday()).isEqualTo(LocalDate.of(2026, 7, 12));
    }

    @Test
    void rejectsUnknownPaymentMethod() {
        Member actor = member("admin-id", "admin", Role.ADMIN);
        OfferingRequest request = request(GivingType.ANONYMOUS, null, "Anonymous");

        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "TITHE"))
            .thenReturn(Optional.of(activeReference("TITHE")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, "CASH"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createOffering(actor, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Payment method was not found.");
    }

    @Test
    void updatesOfferingAndLinkedIncomeTransaction() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        Offering existing = existingOffering();
        FinancialTransaction transaction = linkedTransaction(existing);
        OfferingRequest request = new OfferingRequest(
            GivingType.GROUP,
            null,
            "Youth Group",
            LocalDate.of(2026, 7, 9),
            LocalDate.of(2026, 7, 12),
            "MISSION",
            new BigDecimal("40.00"),
            "CHEQUE",
            "Updated memo"
        );

        when(offeringRepository.findById("offering-id")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.findById("txn-id")).thenReturn(Optional.of(transaction));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.OFFERING_FUND_CATEGORY, "MISSION"))
            .thenReturn(Optional.of(activeReference("MISSION")));
        when(referenceDataRepository.findByTypeAndCode(ReferenceDataType.PAYMENT_METHOD, "CHEQUE"))
            .thenReturn(Optional.of(activeReference(ReferenceDataType.PAYMENT_METHOD, "CHEQUE")));
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offering updated = service().updateOffering(actor, "offering-id", request);

        assertThat(updated.getGivingType()).isEqualTo(GivingType.GROUP);
        assertThat(updated.getGiverDisplayName()).isEqualTo("Youth Group");
        assertThat(updated.getFundCategory()).isEqualTo("MISSION");
        assertThat(updated.getPaymentMethod()).isEqualTo("CHEQUE");
        verify(financialTransactionRepository).save(argThat(saved ->
            saved.getTransactionDate().equals(LocalDate.of(2026, 7, 9))
                && saved.getAmount().compareTo(new BigDecimal("40.00")) == 0
                && "MISSION".equals(saved.getCategory())
                && "Updated memo".equals(saved.getMemo())
        ));
    }

    @Test
    void softDeletesOfferingAndHidesLinkedIncomeFromFinance() {
        Member actor = member("treasurer-id", "treasurer@example.com", Role.TREASURER);
        Offering existing = existingOffering();
        FinancialTransaction transaction = linkedTransaction(existing);

        when(offeringRepository.findById("offering-id")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.findById("txn-id")).thenReturn(Optional.of(transaction));
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Offering deleted = service().deleteOffering(actor, "offering-id");

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedBy()).isEqualTo("treasurer-id");
        assertThat(deleted.getDeletedAt()).isNotNull();
        verify(financialTransactionRepository).save(argThat(saved ->
            saved.isDeleted()
                && "treasurer-id".equals(saved.getDeletedBy())
                && saved.getDeletedAt() != null
                && saved.getMemo().contains("Cancelled offering")
        ));
    }

    private OfferingService service() {
        return new OfferingService(offeringRepository, financialTransactionRepository, memberRepository, referenceDataRepository);
    }

    private Offering existingOffering() {
        Offering offering = new Offering();
        offering.setId("offering-id");
        offering.setGivingType(GivingType.ANONYMOUS);
        offering.setGiverLabel("Anonymous");
        offering.setGiverDisplayName("Anonymous");
        offering.setOfferingDate(LocalDate.of(2026, 7, 8));
        offering.setOfferingSunday(LocalDate.of(2026, 7, 12));
        offering.setFundCategory("TITHE");
        offering.setAmount(new BigDecimal("25.00"));
        offering.setPaymentMethod("CASH");
        offering.setMemo("Sunday offering");
        offering.setIncomeTransactionId("txn-id");
        return offering;
    }

    private FinancialTransaction linkedTransaction(Offering offering) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(offering.getIncomeTransactionId());
        transaction.setType(FinancialTransactionType.INCOME);
        transaction.setTransactionDate(offering.getOfferingDate());
        transaction.setAmount(offering.getAmount());
        transaction.setCategory(offering.getFundCategory());
        transaction.setSourceType(FinancialSourceType.OFFERING);
        transaction.setSourceId(offering.getId());
        transaction.setMemo(offering.getMemo());
        return transaction;
    }

    private OfferingRequest request(GivingType givingType, String memberId, String giverLabel) {
        return new OfferingRequest(
            givingType,
            memberId,
            giverLabel,
            LocalDate.of(2026, 7, 8),
            null,
            "TITHE",
            new BigDecimal("25.00"),
            "Cash",
            "Sunday offering"
        );
    }

    private Member member(String id, String primaryEmail, Role role) {
        Member member = new Member();
        member.setId(id);
        member.setPrimaryEmail(primaryEmail);
        member.setRoles(Set.of(role));
        member.setActive(true);
        return member;
    }

    private ReferenceData activeReference(String code) {
        return activeReference(ReferenceDataType.OFFERING_FUND_CATEGORY, code);
    }

    private ReferenceData activeReference(ReferenceDataType type, String code) {
        ReferenceData referenceData = new ReferenceData();
        referenceData.setType(type);
        referenceData.setCode(code);
        referenceData.setLabel(code);
        referenceData.setActive(true);
        return referenceData;
    }

    private void saveOfferingWithId() {
        when(offeringRepository.save(any(Offering.class))).thenAnswer(invocation -> {
            Offering offering = invocation.getArgument(0);
            if (offering.getId() == null) {
                offering.setId("offering-id");
            }
            return offering;
        });
    }

    private void saveTransactionWithId() {
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction transaction = invocation.getArgument(0);
            transaction.setId("txn-id");
            return transaction;
        });
    }
}
