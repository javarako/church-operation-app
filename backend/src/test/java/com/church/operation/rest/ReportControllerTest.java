package com.church.operation.rest;

import com.church.operation.dto.TaxReceiptSummaryRow;
import com.church.operation.dto.QuarterlyFinancialReport;
import com.church.operation.dto.YearEndClosingReportStatus;
import com.church.operation.dto.YearEndClosingRequest;
import com.church.operation.dto.YearEndClosingStatusResponse;
import com.church.operation.dto.YearlyWorkbookDownload;
import com.church.operation.entity.Member;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.exception.YearEndSnapshotException;
import com.church.operation.service.QuarterlyFinancialExcelService;
import com.church.operation.service.QuarterlyExpenditureReportService;
import com.church.operation.service.QuarterlyOfferingReportService;
import com.church.operation.service.ReportService;
import com.church.operation.service.TaxReceiptPdfService;
import com.church.operation.service.TaxReceiptService;
import com.church.operation.service.YearEndClosingService;
import com.church.operation.util.Role;
import com.church.operation.util.TaxReceiptStatus;
import com.church.operation.util.YearEndClosingStatus;
import com.church.operation.util.YearEndReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReportControllerTest {
    private final ReportService reportService = mock(ReportService.class);
    private final TaxReceiptService taxReceiptService = mock(TaxReceiptService.class);
    private final TaxReceiptPdfService pdfService = mock(TaxReceiptPdfService.class);
    private final QuarterlyOfferingReportService quarterlyReportService = mock(QuarterlyOfferingReportService.class);
    private final QuarterlyExpenditureReportService quarterlyExpenditureReportService =
        mock(QuarterlyExpenditureReportService.class);
    private final QuarterlyFinancialExcelService quarterlyExcelService = mock(QuarterlyFinancialExcelService.class);
    private final YearEndClosingService yearEndClosingService = mock(YearEndClosingService.class);
    private MockMvc mockMvc;
    private Member treasurer;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ReportController(
            reportService,
            taxReceiptService,
            pdfService,
            quarterlyReportService,
            quarterlyExpenditureReportService,
            quarterlyExcelService,
            yearEndClosingService
        ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        treasurer = member(Role.TREASURER);
    }

    @Test
    void returnsAnnualSummaryUsingExplicitFilters() throws Exception {
        when(taxReceiptService.summary(treasurer, 2026, "1001")).thenReturn(List.of(
            new TaxReceiptSummaryRow("m1", "1001", "Ada Wong", "100 Main St", 2026,
                new BigDecimal("45.00"), null, null, null, false)
        ));

        mockMvc.perform(get("/api/reports/tax-receipts/summary")
                .param("taxYear", "2026")
                .param("offeringNumber", "1001")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].offeringNumber").value("1001"))
            .andExpect(jsonPath("$[0].totalAmount").value(45.00));
    }

    @Test
    void issuesIndividualReceipt() throws Exception {
        TaxReceipt receipt = receipt("r1", "2026-000001");
        when(taxReceiptService.issue(treasurer, 2026, "1001", "Thank you")).thenReturn(receipt);

        mockMvc.perform(post("/api/reports/tax-receipts/issue")
                .contentType("application/json")
                .content("{\"taxYear\":2026,\"offeringNumber\":\"1001\",\"thankYouNote\":\"Thank you\"}")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receiptNumber").value("2026-000001"));
    }

    @Test
    void downloadsReceiptPdfWithAttachmentHeaders() throws Exception {
        TaxReceipt receipt = receipt("r1", "2026-000001");
        when(taxReceiptService.findById(treasurer, "r1")).thenReturn(receipt);
        when(pdfService.render(receipt)).thenReturn("pdf-data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/reports/tax-receipts/r1/pdf").principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=receipt-2026-000001.pdf"))
            .andExpect(content().bytes("pdf-data".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadsQuarterlyOfferingWorkbookWithAttachmentHeaders() throws Exception {
        Member viewer = member(Role.VIEWER);
        QuarterlyFinancialReport report = new QuarterlyFinancialReport(
            2026,
            2,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30),
            2026,
            LocalDate.of(2026, 1, 1),
            List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
            List.of(),
            BigDecimal.ZERO,
            List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            BigDecimal.ZERO,
            "Offering income",
            "수입",
            "전년도 이월금"
        );
        byte[] workbook = new byte[] {1, 2, 3};
        when(quarterlyReportService.build(viewer, 2026, 2)).thenReturn(report);
        when(quarterlyExcelService.render(report)).thenReturn(workbook);

        mockMvc.perform(get("/api/reports/quarterly-offerings.xlsx")
                .param("year", "2026")
                .param("quarter", "2")
                .principal(authentication(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=quarterly-offerings-2026-q2.xlsx"
            ))
            .andExpect(content().bytes(workbook));

        verify(quarterlyReportService).build(viewer, 2026, 2);
        verify(quarterlyExcelService).render(report);
    }

    @Test
    void downloadsQuarterlyExpenditureWorkbookWithAttachmentHeaders() throws Exception {
        Member viewer = member(Role.VIEWER);
        QuarterlyFinancialReport report = new QuarterlyFinancialReport(
            2026,
            2,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30),
            2026,
            LocalDate.of(2026, 1, 1),
            List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
            List.of(),
            BigDecimal.ZERO,
            List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            BigDecimal.ZERO,
            "Expenditure",
            "지출",
            "CONTINGENCY"
        );
        byte[] workbook = new byte[] {4, 5, 6};
        when(quarterlyExpenditureReportService.build(viewer, 2026, 2)).thenReturn(report);
        when(quarterlyExcelService.render(report)).thenReturn(workbook);

        mockMvc.perform(get("/api/reports/quarterly-expenditures.xlsx")
                .param("year", "2026")
                .param("quarter", "2")
                .principal(authentication(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=quarterly-expenditures-2026-q2.xlsx"
            ))
            .andExpect(content().bytes(workbook));

        verify(quarterlyExpenditureReportService).build(viewer, 2026, 2);
        verify(quarterlyExcelService).render(report);
    }

    @Test
    void rejectsInvalidQuarterlyReportSelection() throws Exception {
        Member viewer = member(Role.VIEWER);
        when(quarterlyReportService.build(viewer, 2026, 5))
            .thenThrow(new IllegalArgumentException("A valid calendar year and quarter are required."));

        mockMvc.perform(get("/api/reports/quarterly-offerings.xlsx")
                .param("year", "2026")
                .param("quarter", "5")
                .principal(authentication(viewer)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidQuarterlyExpenditureSelection() throws Exception {
        Member viewer = member(Role.VIEWER);
        when(quarterlyExpenditureReportService.build(viewer, 2026, 5))
            .thenThrow(new IllegalArgumentException("A valid calendar year and quarter are required."));

        mockMvc.perform(get("/api/reports/quarterly-expenditures.xlsx")
                .param("year", "2026")
                .param("quarter", "5")
                .principal(authentication(viewer)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void downloadsYearlyOfferingWorkbookWithAttachmentHeaders() throws Exception {
        Member viewer = member(Role.VIEWER);
        byte[] workbook = new byte[] {7, 8, 9};
        when(yearEndClosingService.download(viewer, YearEndReportType.OFFERING, 2026))
            .thenReturn(new YearlyWorkbookDownload(
                workbook,
                "yearly-offerings-2026-closed-v2.xlsx"
            ));

        mockMvc.perform(get("/api/reports/yearly-offerings.xlsx")
                .param("fiscalYear", "2026")
                .principal(authentication(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=yearly-offerings-2026-closed-v2.xlsx"
            ))
            .andExpect(content().bytes(workbook));

        verify(yearEndClosingService).download(viewer, YearEndReportType.OFFERING, 2026);
    }

    @Test
    void downloadsYearlyExpenditureWorkbookWithAttachmentHeaders() throws Exception {
        Member viewer = member(Role.VIEWER);
        byte[] workbook = new byte[] {10, 11, 12};
        when(yearEndClosingService.download(viewer, YearEndReportType.EXPENDITURE, 2026))
            .thenReturn(new YearlyWorkbookDownload(
                workbook,
                "yearly-expenditures-2026-draft.xlsx"
            ));

        mockMvc.perform(get("/api/reports/yearly-expenditures.xlsx")
                .param("fiscalYear", "2026")
                .principal(authentication(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=yearly-expenditures-2026-draft.xlsx"
            ))
            .andExpect(content().bytes(workbook));

        verify(yearEndClosingService).download(viewer, YearEndReportType.EXPENDITURE, 2026);
    }

    @Test
    void rejectsInvalidYearlyReportSelection() throws Exception {
        Member viewer = member(Role.VIEWER);
        when(yearEndClosingService.download(viewer, YearEndReportType.OFFERING, 1999))
            .thenThrow(new IllegalArgumentException("A valid fiscal year is required."));

        mockMvc.perform(get("/api/reports/yearly-offerings.xlsx")
                .param("fiscalYear", "1999")
                .principal(authentication(viewer)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidYearlyExpenditureSelection() throws Exception {
        Member viewer = member(Role.VIEWER);
        when(yearEndClosingService.download(viewer, YearEndReportType.EXPENDITURE, 1999))
            .thenThrow(new IllegalArgumentException("A valid fiscal year is required."));

        mockMvc.perform(get("/api/reports/yearly-expenditures.xlsx")
                .param("fiscalYear", "1999")
                .principal(authentication(viewer)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMemberRolesFromBothYearlyDownloads() throws Exception {
        for (Role role : List.of(Role.MEMBER, Role.MEMBERSHIP)) {
            Member unauthorized = member(role);
            when(yearEndClosingService.download(unauthorized, YearEndReportType.OFFERING, 2026))
                .thenThrow(new SecurityException("You do not have permission to view reports."));
            when(yearEndClosingService.download(unauthorized, YearEndReportType.EXPENDITURE, 2026))
                .thenThrow(new SecurityException("You do not have permission to view reports."));

            mockMvc.perform(get("/api/reports/yearly-offerings.xlsx")
                    .param("fiscalYear", "2026")
                    .principal(authentication(unauthorized)))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/reports/yearly-expenditures.xlsx")
                    .param("fiscalYear", "2026")
                    .principal(authentication(unauthorized)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void returnsIndependentYearEndClosingStatus() throws Exception {
        Instant closedAt = Instant.parse("2026-07-21T19:42:00Z");
        YearEndClosingStatusResponse response = new YearEndClosingStatusResponse(
            2025,
            LocalDate.of(2025, 12, 31),
            true,
            new YearEndClosingReportStatus(
                YearEndReportType.OFFERING,
                YearEndClosingStatus.CLOSED,
                2,
                closedAt
            ),
            new YearEndClosingReportStatus(
                YearEndReportType.EXPENDITURE,
                YearEndClosingStatus.NOT_CLOSED,
                null,
                null
            )
        );
        when(yearEndClosingService.status(treasurer, 2025)).thenReturn(response);

        mockMvc.perform(get("/api/reports/yearly-closing-status")
                .param("fiscalYear", "2025")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closeEligible").value(true))
            .andExpect(jsonPath("$.offering.status").value("CLOSED"))
            .andExpect(jsonPath("$.offering.version").value(2))
            .andExpect(jsonPath("$.expenditure.status").value("NOT_CLOSED"));
    }

    @Test
    void closesAndReopensSelectedYearEndReport() throws Exception {
        YearEndClosingReportStatus closed = new YearEndClosingReportStatus(
            YearEndReportType.OFFERING,
            YearEndClosingStatus.CLOSED,
            1,
            Instant.parse("2026-07-21T19:42:00Z")
        );
        YearEndClosingReportStatus reopened = new YearEndClosingReportStatus(
            YearEndReportType.OFFERING,
            YearEndClosingStatus.REOPENED,
            1,
            Instant.parse("2026-07-22T13:15:00Z")
        );
        YearEndClosingRequest request = new YearEndClosingRequest(2025, "secret-value");
        when(yearEndClosingService.close(treasurer, YearEndReportType.OFFERING, request))
            .thenReturn(closed);
        when(yearEndClosingService.reopen(treasurer, YearEndReportType.OFFERING, request))
            .thenReturn(reopened);

        String body = "{\"fiscalYear\":2025,\"currentPassword\":\"secret-value\"}";
        mockMvc.perform(post("/api/reports/yearly-closing/OFFERING/close")
                .contentType("application/json")
                .content(body)
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/api/reports/yearly-closing/OFFERING/reopen")
                .contentType("application/json")
                .content(body)
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REOPENED"));
    }

    @Test
    void rejectsBlankClosingPassword() throws Exception {
        mockMvc.perform(post("/api/reports/yearly-closing/EXPENDITURE/close")
                .contentType("application/json")
                .content("{\"fiscalYear\":2025,\"currentPassword\":\"\"}")
                .principal(authentication(treasurer)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsActionableYearEndSnapshotErrors() throws Exception {
        when(yearEndClosingService.download(treasurer, YearEndReportType.OFFERING, 2025))
            .thenThrow(new YearEndSnapshotException(
                "Closed yearly workbook checksum verification failed."
            ));

        mockMvc.perform(get("/api/reports/yearly-offerings.xlsx")
                .param("fiscalYear", "2025")
                .principal(authentication(treasurer)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("YEAR_END_SNAPSHOT_ERROR"))
            .andExpect(jsonPath("$.message").value(
                "Closed yearly workbook checksum verification failed."
            ));
    }

    @Test
    void downloadsBatchAsZipWithOneEntryPerReceipt() throws Exception {
        TaxReceipt first = receipt("r1", "2026-000001");
        TaxReceipt second = receipt("r2", "2026-000002");
        when(taxReceiptService.issueBatch(treasurer, 2026, "Thank you")).thenReturn(List.of(first, second));
        when(pdfService.render(first)).thenReturn("first".getBytes(StandardCharsets.UTF_8));
        when(pdfService.render(second)).thenReturn("second".getBytes(StandardCharsets.UTF_8));

        byte[] zip = mockMvc.perform(post("/api/reports/tax-receipts/issue-batch")
                .contentType("application/json")
                .content("{\"taxYear\":2026,\"thankYouNote\":\"Thank you\"}")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=tax-receipts-2026.zip"))
            .andReturn().getResponse().getContentAsByteArray();

        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry firstEntry = input.getNextEntry();
            assertThat(firstEntry.getName()).isEqualTo("receipt-2026-000001.pdf");
            ZipEntry secondEntry = input.getNextEntry();
            assertThat(secondEntry.getName()).isEqualTo("receipt-2026-000002.pdf");
            assertThat(input.getNextEntry()).isNull();
        }
    }

    @Test
    void voidsAndReplacesReceipt() throws Exception {
        TaxReceipt voided = receipt("r1", "2026-000001");
        voided.setStatus(TaxReceiptStatus.VOID);
        TaxReceipt replacement = receipt("r2", "2026-000002");
        when(taxReceiptService.voidReceipt(treasurer, "r1", "Incorrect donor address")).thenReturn(voided);
        when(taxReceiptService.replaceReceipt(treasurer, "r1", "Updated thanks")).thenReturn(replacement);

        mockMvc.perform(post("/api/reports/tax-receipts/r1/void")
                .contentType("application/json")
                .content("{\"reason\":\"Incorrect donor address\"}")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VOID"));
        mockMvc.perform(post("/api/reports/tax-receipts/r1/replace")
                .contentType("application/json")
                .content("{\"thankYouNote\":\"Updated thanks\"}")
                .principal(authentication(treasurer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receiptNumber").value("2026-000002"));
    }

    @Test
    void viewerIsForbidden() throws Exception {
        Member viewer = member(Role.VIEWER);
        when(taxReceiptService.summary(viewer, 2026, null))
            .thenThrow(new SecurityException("You do not have permission to manage official tax receipts."));

        mockMvc.perform(get("/api/reports/tax-receipts/summary")
                .param("taxYear", "2026")
                .principal(authentication(viewer)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private TaxReceipt receipt(String id, String number) {
        TaxReceipt receipt = new TaxReceipt();
        receipt.setId(id);
        receipt.setReceiptNumber(number);
        receipt.setStatus(TaxReceiptStatus.ISSUED);
        return receipt;
    }

    private TestingAuthenticationToken authentication(Member member) {
        return new TestingAuthenticationToken(member, null);
    }

    private Member member(Role role) {
        Member member = new Member();
        member.setId(role.name().toLowerCase());
        member.setRoles(Set.of(role));
        return member;
    }
}
