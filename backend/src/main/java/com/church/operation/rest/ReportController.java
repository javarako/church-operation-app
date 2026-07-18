package com.church.operation.rest;

import com.church.operation.dto.FinancialBudgetReportRow;
import com.church.operation.dto.MemberOfferingSummaryReportRow;
import com.church.operation.dto.TaxReceiptBatchIssueRequest;
import com.church.operation.dto.TaxReceiptIssueRequest;
import com.church.operation.dto.TaxReceiptNoteRequest;
import com.church.operation.dto.TaxReceiptSummaryRow;
import com.church.operation.dto.VoidTaxReceiptRequest;
import com.church.operation.dto.WeeklyOfferingReportRow;
import com.church.operation.entity.Member;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.service.ReportService;
import com.church.operation.service.QuarterlyFinancialExcelService;
import com.church.operation.service.QuarterlyExpenditureReportService;
import com.church.operation.service.QuarterlyOfferingReportService;
import com.church.operation.service.TaxReceiptPdfService;
import com.church.operation.service.TaxReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;
    private final TaxReceiptService taxReceiptService;
    private final TaxReceiptPdfService taxReceiptPdfService;
    private final QuarterlyOfferingReportService quarterlyOfferingReportService;
    private final QuarterlyExpenditureReportService quarterlyExpenditureReportService;
    private final QuarterlyFinancialExcelService quarterlyFinancialExcelService;

    public ReportController(
        ReportService reportService,
        TaxReceiptService taxReceiptService,
        TaxReceiptPdfService taxReceiptPdfService,
        QuarterlyOfferingReportService quarterlyOfferingReportService,
        QuarterlyExpenditureReportService quarterlyExpenditureReportService,
        QuarterlyFinancialExcelService quarterlyFinancialExcelService
    ) {
        this.reportService = reportService;
        this.taxReceiptService = taxReceiptService;
        this.taxReceiptPdfService = taxReceiptPdfService;
        this.quarterlyOfferingReportService = quarterlyOfferingReportService;
        this.quarterlyExpenditureReportService = quarterlyExpenditureReportService;
        this.quarterlyFinancialExcelService = quarterlyFinancialExcelService;
    }

    @GetMapping("/weekly-offerings")
    List<WeeklyOfferingReportRow> weeklyOfferings(
        Authentication authentication,
        @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @RequestParam(value = "fundCode", required = false) String fundCode,
        @RequestParam(value = "categoryCode", required = false) String categoryCode,
        @RequestParam(value = "paymentMethod", required = false) String paymentMethod
    ) {
        return reportService.weeklyOfferings(actor(authentication), start, end, fundCode, categoryCode, paymentMethod);
    }

    @GetMapping("/member-offerings")
    List<MemberOfferingSummaryReportRow> memberOfferings(
        Authentication authentication,
        @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @RequestParam(value = "offeringNumber", required = false) String offeringNumber,
        @RequestParam(value = "fundCode", required = false) String fundCode,
        @RequestParam(value = "categoryCode", required = false) String categoryCode
    ) {
        return reportService.memberOfferings(actor(authentication), start, end, offeringNumber, fundCode, categoryCode);
    }

    @GetMapping("/tax-receipts/summary")
    List<TaxReceiptSummaryRow> taxReceiptSummary(
        Authentication authentication,
        @RequestParam("taxYear") int taxYear,
        @RequestParam(value = "offeringNumber", required = false) String offeringNumber
    ) {
        return taxReceiptService.summary(actor(authentication), taxYear, offeringNumber);
    }

    @PostMapping("/tax-receipts/issue")
    TaxReceipt issueTaxReceipt(
        Authentication authentication,
        @Valid @RequestBody TaxReceiptIssueRequest request
    ) {
        return taxReceiptService.issue(
            actor(authentication), request.taxYear(), request.offeringNumber(), request.thankYouNote()
        );
    }

    @PostMapping("/tax-receipts/issue-batch")
    ResponseEntity<byte[]> issueTaxReceiptBatch(
        Authentication authentication,
        @Valid @RequestBody TaxReceiptBatchIssueRequest request
    ) {
        List<TaxReceipt> receipts = taxReceiptService.issueBatch(
            actor(authentication), request.taxYear(), request.thankYouNote()
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tax-receipts-" + request.taxYear() + ".zip")
            .body(zip(receipts));
    }

    @GetMapping("/tax-receipts/{receiptId}/pdf")
    ResponseEntity<byte[]> downloadTaxReceipt(
        Authentication authentication,
        @PathVariable("receiptId") String receiptId
    ) {
        TaxReceipt receipt = taxReceiptService.findById(actor(authentication), receiptId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=receipt-" + receipt.getReceiptNumber() + ".pdf")
            .body(taxReceiptPdfService.render(receipt));
    }

    @PostMapping("/tax-receipts/{receiptId}/void")
    TaxReceipt voidTaxReceipt(
        Authentication authentication,
        @PathVariable("receiptId") String receiptId,
        @Valid @RequestBody VoidTaxReceiptRequest request
    ) {
        return taxReceiptService.voidReceipt(actor(authentication), receiptId, request.reason());
    }

    @PostMapping("/tax-receipts/{receiptId}/replace")
    TaxReceipt replaceTaxReceipt(
        Authentication authentication,
        @PathVariable("receiptId") String receiptId,
        @Valid @RequestBody TaxReceiptNoteRequest request
    ) {
        return taxReceiptService.replaceReceipt(actor(authentication), receiptId, request.thankYouNote());
    }

    @GetMapping("/financial-budget")
    List<FinancialBudgetReportRow> financialBudget(
        Authentication authentication,
        @RequestParam("fiscalYear") int fiscalYear
    ) {
        return reportService.financialBudget(actor(authentication), fiscalYear);
    }

    @GetMapping("/quarterly-offerings.xlsx")
    ResponseEntity<byte[]> downloadQuarterlyOfferings(
        Authentication authentication,
        @RequestParam("year") int year,
        @RequestParam("quarter") int quarter
    ) {
        byte[] workbook = quarterlyFinancialExcelService.render(
            quarterlyOfferingReportService.build(actor(authentication), year, quarter)
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=quarterly-offerings-" + year + "-q" + quarter + ".xlsx"
            )
            .body(workbook);
    }

    @GetMapping("/quarterly-expenditures.xlsx")
    ResponseEntity<byte[]> downloadQuarterlyExpenditures(
        Authentication authentication,
        @RequestParam("year") int year,
        @RequestParam("quarter") int quarter
    ) {
        byte[] workbook = quarterlyFinancialExcelService.render(
            quarterlyExpenditureReportService.build(actor(authentication), year, quarter)
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=quarterly-expenditures-" + year + "-q" + quarter + ".xlsx"
            )
            .body(workbook);
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }

    private byte[] zip(List<TaxReceipt> receipts) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (TaxReceipt receipt : receipts) {
                zip.putNextEntry(new ZipEntry("receipt-" + receipt.getReceiptNumber() + ".pdf"));
                zip.write(taxReceiptPdfService.render(receipt));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create the tax receipt ZIP.", ex);
        }
    }
}
