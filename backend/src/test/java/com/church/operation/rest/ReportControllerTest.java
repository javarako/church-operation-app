package com.church.operation.rest;

import com.church.operation.dto.TaxReceiptSummaryRow;
import com.church.operation.entity.Member;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.exception.GlobalExceptionHandler;
import com.church.operation.service.ReportService;
import com.church.operation.service.TaxReceiptPdfService;
import com.church.operation.service.TaxReceiptService;
import com.church.operation.util.Role;
import com.church.operation.util.TaxReceiptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    private MockMvc mockMvc;
    private Member treasurer;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ReportController(reportService, taxReceiptService, pdfService))
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
