package com.church.operation.rest;

import com.church.operation.dto.FinancialBudgetReportRow;
import com.church.operation.dto.MemberOfferingSummaryReportRow;
import com.church.operation.dto.OfficialTaxReportRow;
import com.church.operation.dto.WeeklyOfferingReportRow;
import com.church.operation.entity.Member;
import com.church.operation.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/weekly-offerings")
    List<WeeklyOfferingReportRow> weeklyOfferings(
        Authentication authentication,
        @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @RequestParam(value = "fundCategory", required = false) String fundCategory,
        @RequestParam(value = "paymentMethod", required = false) String paymentMethod
    ) {
        return reportService.weeklyOfferings(actor(authentication), start, end, fundCategory, paymentMethod);
    }

    @GetMapping("/member-offerings")
    List<MemberOfferingSummaryReportRow> memberOfferings(
        Authentication authentication,
        @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
        @RequestParam(value = "memberId", required = false) String memberId,
        @RequestParam(value = "fundCategory", required = false) String fundCategory
    ) {
        return reportService.memberOfferings(actor(authentication), start, end, memberId, fundCategory);
    }

    @GetMapping("/tax-return")
    List<OfficialTaxReportRow> taxReturn(
        Authentication authentication,
        @RequestParam("taxYear") int taxYear,
        @RequestParam(value = "memberId", required = false) String memberId
    ) {
        return reportService.officialTaxReturn(actor(authentication), taxYear, memberId);
    }

    @GetMapping("/financial-budget")
    List<FinancialBudgetReportRow> financialBudget(
        Authentication authentication,
        @RequestParam("fiscalYear") int fiscalYear
    ) {
        return reportService.financialBudget(actor(authentication), fiscalYear);
    }

    private Member actor(Authentication authentication) {
        return (Member) authentication.getPrincipal();
    }
}
