package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.TaxReceiptSummaryRow;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxReceiptServiceTest {
    private static final String DEFAULT_NOTE = "Thank you for your faithful and generous support over the past year. Because of you, we are able to continue serving our community and sharing God's message.";

    @Mock private OfferingRepository offeringRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private TaxReceiptRepository receiptRepository;
    @Mock private TaxReceiptCounterService counterService;
    private TaxReceiptService service;

    @BeforeEach
    void setUp() {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Grace Church", "1 Hope Rd, Toronto, ON", "416-555-0100", "Daniel Kim",
                "123456789RR0001", "Toronto, Ontario", "https://grace.example.org"
            ),
            new ChurchInformationProperties.Branding("/banner.png", "/logo.png"),
            new ChurchInformationProperties.Ui(20)
        );
        service = new TaxReceiptService(
            offeringRepository,
            memberRepository,
            receiptRepository,
            counterService,
            properties,
            Clock.fixed(Instant.parse("2027-02-15T15:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void summaryGroupsMemberOfferingsByCalendarYearAndSortsOfferingNumber() {
        Member memberTwo = member("m2", "1002", "Ben Park", true);
        Member memberOne = member("m1", "1001", "Ada Wong", true);
        when(memberRepository.findAll()).thenReturn(List.of(memberTwo, memberOne));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
            offering("o1", "m2", "30.00"),
            offering("o2", "m1", "20.00"),
            offering("o3", "m1", "25.00"),
            anonymousOffering("o4", "40.00")
        ));

        List<TaxReceiptSummaryRow> rows = service.summary(treasurer(), 2026, null);

        assertThat(rows)
            .extracting(TaxReceiptSummaryRow::offeringNumber, TaxReceiptSummaryRow::totalAmount)
            .containsExactly(tuple("1001", new BigDecimal("45.00")), tuple("1002", new BigDecimal("30.00")));
    }

    @Test
    void issuanceRejectsMissingDonorAddressBeforeAllocatingSerial() {
        Member member = member("m1", "1001", "Ada Wong", false);
        member.setMailingAddress(null);
        when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering("o1", "m1", "45.00")));
        when(receiptRepository.findFirstByTaxYearAndOfferingNumberAndStatusOrderByCreatedAtDesc(
            2026, "1001", TaxReceiptStatus.ISSUED
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(treasurer(), 2026, "1001", DEFAULT_NOTE))
            .isInstanceOf(ReceiptValidationException.class)
            .hasMessageContaining("donor address");
        verifyNoInteractions(counterService);
    }

    @Test
    void reissueReturnsExistingSnapshotWithoutNewSerial() {
        TaxReceipt existing = new TaxReceipt();
        existing.setReceiptNumber("2026-000001");
        existing.setStatus(TaxReceiptStatus.ISSUED);
        when(receiptRepository.findFirstByTaxYearAndOfferingNumberAndStatusOrderByCreatedAtDesc(
            2026, "1001", TaxReceiptStatus.ISSUED
        )).thenReturn(Optional.of(existing));

        assertThat(service.issue(treasurer(), 2026, "1001", DEFAULT_NOTE).getReceiptNumber())
            .isEqualTo("2026-000001");
        verifyNoInteractions(counterService);
    }

    @Test
    void issuancePersistsImmutableDonorChurchSourceAndNoteSnapshot() {
        Member member = member("m1", "1001", "Ada Wong", true);
        Offering first = offering("o2", "m1", "25.00");
        Offering second = offering("o1", "m1", "20.00");
        when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(first, second));
        when(receiptRepository.findFirstByTaxYearAndOfferingNumberAndStatusOrderByCreatedAtDesc(
            2026, "1001", TaxReceiptStatus.ISSUED
        )).thenReturn(Optional.empty());
        when(counterService.nextReceiptNumber(2026)).thenReturn("2026-000001");
        when(receiptRepository.save(org.mockito.ArgumentMatchers.any(TaxReceipt.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TaxReceipt receipt = service.issue(treasurer(), 2026, "1001", DEFAULT_NOTE);

        assertThat(receipt.getReceiptNumber()).isEqualTo("2026-000001");
        assertThat(receipt.getDonorName()).isEqualTo("Ada Wong");
        assertThat(receipt.getDonorAddress()).contains("100 Main St", "Toronto", "Canada");
        assertThat(receipt.getChurchName()).isEqualTo("Grace Church");
        assertThat(receipt.getCharityRegistrationNumber()).isEqualTo("123456789RR0001");
        assertThat(receipt.getGiftAmount()).isEqualByComparingTo("45.00");
        assertThat(receipt.getEligibleAmount()).isEqualByComparingTo("45.00");
        assertThat(receipt.getAdvantageAmount()).isEqualByComparingTo("0.00");
        assertThat(receipt.getSourceOfferingIds()).containsExactly("o1", "o2");
        assertThat(receipt.getSourceChecksum()).isNotBlank();
        assertThat(receipt.getThankYouNote()).isEqualTo(DEFAULT_NOTE);
    }

    @Test
    void summaryWarnsWhenCurrentOfferingSourcesDifferFromIssuedSnapshot() {
        Member member = member("m1", "1001", "Ada Wong", true);
        TaxReceipt receipt = new TaxReceipt();
        receipt.setId("r1");
        receipt.setReceiptNumber("2026-000001");
        receipt.setStatus(TaxReceiptStatus.ISSUED);
        receipt.setSourceChecksum("outdated-checksum");
        when(memberRepository.findAll()).thenReturn(List.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering("o1", "m1", "45.00")));
        when(receiptRepository.findFirstByTaxYearAndOfferingNumberOrderByCreatedAtDesc(2026, "1001"))
            .thenReturn(Optional.of(receipt));

        assertThat(service.summary(treasurer(), 2026, null)).singleElement()
            .satisfies(row -> assertThat(row.sourceChanged()).isTrue());
    }

    @Test
    void summaryReturnsLatestVoidReceiptForReplacementAction() {
        Member member = member("m1", "1001", "Ada Wong", true);
        TaxReceipt receipt = new TaxReceipt();
        receipt.setId("r1");
        receipt.setReceiptNumber("2026-000001");
        receipt.setStatus(TaxReceiptStatus.VOID);
        when(memberRepository.findAll()).thenReturn(List.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering("o1", "m1", "45.00")));
        when(receiptRepository.findFirstByTaxYearAndOfferingNumberOrderByCreatedAtDesc(2026, "1001"))
            .thenReturn(Optional.of(receipt));

        assertThat(service.summary(treasurer(), 2026, null)).singleElement().satisfies(row -> {
            assertThat(row.receiptId()).isEqualTo("r1");
            assertThat(row.receiptStatus()).isEqualTo(TaxReceiptStatus.VOID);
        });
    }

    @Test
    void batchPrevalidatesEveryDonorAndIgnoresAdminWithoutOfferingNumber() {
        Member admin = new Member();
        admin.setId("admin");
        Member valid = member("m1", "1001", "Ada Wong", true);
        Member invalid = member("m2", "1002", "Ben Park", true);
        invalid.setMailingAddress(null);
        when(memberRepository.findAll()).thenReturn(List.of(admin, valid, invalid));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering("o1", "m1", "20.00"), offering("o2", "m2", "30.00")));

        assertThatThrownBy(() -> service.issueBatch(treasurer(), 2026, DEFAULT_NOTE))
            .isInstanceOf(ReceiptValidationException.class)
            .hasMessageContaining("donor address");
        verifyNoInteractions(counterService);
    }

    @Test
    void replacementUsesNextSerialAndLinksBothReceipts() {
        Member member = member("m1", "1001", "Ada Wong", true);
        TaxReceipt original = new TaxReceipt();
        original.setId("r1");
        original.setTaxYear(2026);
        original.setOfferingNumber("1001");
        original.setDonorName("Ada Wong");
        original.setStatus(TaxReceiptStatus.VOID);
        when(receiptRepository.findById("r1")).thenReturn(Optional.of(original));
        when(memberRepository.findByOfferingNumber("1001")).thenReturn(Optional.of(member));
        when(offeringRepository.findByDeletedFalseAndOfferingDateBetweenOrderByOfferingDateAscCreatedAtAsc(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(offering("o1", "m1", "45.00")));
        when(counterService.nextReceiptNumber(2026)).thenReturn("2026-000002");
        when(receiptRepository.save(org.mockito.ArgumentMatchers.any(TaxReceipt.class)))
            .thenAnswer(invocation -> {
                TaxReceipt saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId("r2");
                return saved;
            });

        TaxReceipt replacement = service.replaceReceipt(treasurer(), "r1", DEFAULT_NOTE);

        assertThat(replacement.getReceiptNumber()).isEqualTo("2026-000002");
        assertThat(replacement.getReplacesReceiptId()).isEqualTo("r1");
        assertThat(original.getReplacementReceiptId()).isEqualTo("r2");
        verify(receiptRepository).save(original);
    }

    private Member treasurer() {
        Member member = new Member();
        member.setId("treasurer");
        member.setRoles(Set.of(Role.TREASURER));
        return member;
    }

    private Member member(String id, String offeringNumber, String name, boolean active) {
        Member member = new Member();
        member.setId(id);
        member.setOfferingNumber(offeringNumber);
        member.setDisplayName(name);
        member.setPrimaryEmail(name.toLowerCase().replace(' ', '.') + "@example.org");
        member.setMailingAddress(new Address("100 Main St", null, "Toronto", "ON", "M1M 1M1", "Canada"));
        member.setActive(active);
        return member;
    }

    private Offering offering(String id, String memberId, String amount) {
        Offering offering = new Offering();
        offering.setId(id);
        offering.setGivingType(GivingType.MEMBER);
        offering.setMemberId(memberId);
        offering.setOfferingDate(LocalDate.of(2026, 6, 15));
        offering.setAmount(new BigDecimal(amount));
        return offering;
    }

    private Offering anonymousOffering(String id, String amount) {
        Offering offering = offering(id, null, amount);
        offering.setGivingType(GivingType.ANONYMOUS);
        return offering;
    }
}
