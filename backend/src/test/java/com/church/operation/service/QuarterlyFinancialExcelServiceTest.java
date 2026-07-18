package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.QuarterlyFinancialGroup;
import com.church.operation.dto.QuarterlyFinancialReport;
import com.church.operation.dto.QuarterlyFinancialRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuarterlyFinancialExcelServiceTest {
    @Test
    void rendersSampleStructureFormulasAndTotals() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(report());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Offering income");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("2026 년도 2분기 수입");
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("4월");
            assertThat(sheet.getRow(3).getCell(4).getStringCellValue()).isEqualTo("5월");
            assertThat(sheet.getRow(3).getCell(5).getStringCellValue()).isEqualTo("6월");

            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("General Fund");
            assertThat(sheet.getRow(4).getCell(0).getCellStyle().getAlignment())
                .isEqualTo(HorizontalAlignment.CENTER);
            assertThat(sheet.getRow(4).getCell(0).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getRow(0).getCell(0).getCellStyle().getBorderBottom())
                .isEqualTo(BorderStyle.NONE);
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Tithe");
            assertThat(sheet.getRow(4).getCell(6).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(sheet.getRow(4).getCell(6).getCellFormula()).isEqualTo("SUM(D5:F5)");
            assertThat(sheet.getRow(4).getCell(8).getCellFormula())
                .isEqualTo("IF(C5=0,\"-\",H5/C5)");

            assertThat(sheet.getRow(6).getCell(1).getStringCellValue()).isEqualTo("(1) 소 계");
            assertThat(sheet.getRow(8).getCell(1).getStringCellValue()).isEqualTo("(2) 소 계");
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("(1) + (2) 합 계");
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("전년도 이월금");
            assertThat(sheet.getRow(10).getCell(2).getNumericCellValue()).isEqualTo(2500d);
            assertThat(sheet.getRow(10).getCell(5).getNumericCellValue()).isEqualTo(999d);
            assertThat(sheet.getRow(10).getCell(6).getCellFormula()).isEqualTo("SUM(D11:F11)");
            assertThat(sheet.getRow(10).getCell(7).getNumericCellValue()).isEqualTo(999d);
            assertThat(sheet.getRow(10).getCell(8).getCellFormula())
                .isEqualTo("IF(C11=0,\"-\",H11/C11)");
            assertThat(sheet.getRow(11).getCell(0).getStringCellValue()).isEqualTo("총 합 계");
            assertThat(sheet.getRow(11).getCell(5).getCellFormula()).isEqualTo("F10+F11");
            assertThat(sheet.getRow(11).getCell(7).getCellFormula()).isEqualTo("H10+H11");

            assertThat(sheet.getMergedRegions()).contains(
                new CellRangeAddress(1, 1, 0, 9),
                new CellRangeAddress(4, 6, 0, 0),
                new CellRangeAddress(7, 8, 0, 0),
                new CellRangeAddress(9, 9, 0, 1),
                new CellRangeAddress(10, 10, 0, 1),
                new CellRangeAddress(11, 11, 0, 1)
            );
        }
    }

    @Test
    void embedsConfiguredLogoAndAppliesPrintLayout() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(report());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Offering income");
            assertThat(workbook.getAllPictures()).hasSize(1);
            XSSFPicture picture = (XSSFPicture) sheet.getDrawingPatriarch().getShapes().getFirst();
            assertThat(picture.getClientAnchor().getCol1()).isEqualTo((short) 7);
            assertThat(picture.getClientAnchor().getCol2()).isEqualTo((short) 10);
            assertThat(picture.getClientAnchor().getRow1()).isEqualTo(0);
            assertThat(picture.getClientAnchor().getRow2()).isEqualTo(1);
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertAdjustTo100Percent(sheet);
            assertThat(sheet.getHorizontallyCenter()).isTrue();
            assertThat(sheet.getMargin(Sheet.TopMargin)).isEqualTo(0.5);
            assertThat(sheet.getMargin(Sheet.BottomMargin)).isEqualTo(0.5);
            assertThat(sheet.getMargin(Sheet.LeftMargin)).isEqualTo(0.25);
            assertThat(sheet.getMargin(Sheet.RightMargin)).isEqualTo(0.25);
            assertThat(sheet.getFooter().getCenter()).isEqualTo("Page &P");
            assertThat(sheet.getRepeatingRows()).isEqualTo(CellRangeAddress.valueOf("1:4"));
            assertThat(workbook.getPrintArea(0)).contains("$A$1:$J$12");
            assertQuarterlyColumnLayout(sheet);

            Font titleFont = workbook.getFontAt(sheet.getRow(1).getCell(0).getCellStyle().getFontIndex());
            assertThat(titleFont.getFontName()).isEqualTo("Arial");
            assertThat(titleFont.getFontHeightInPoints()).isEqualTo((short) 18);
            assertThat(titleFont.getBold()).isTrue();
            assertThat(titleFont.getUnderline()).isEqualTo(Font.U_SINGLE);
        }
    }

    @Test
    void stillRendersWhenConfiguredLogoIsMissing() throws Exception {
        byte[] bytes = service("/branding/not-found.png").render(report());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getAllPictures()).isEmpty();
            assertThat(workbook.getSheet("Offering income")).isNotNull();
        }
    }

    @Test
    void leavesZeroAmountsBlankAndShowsDashOnlyForZeroBudgetPercentage() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(report());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Offering income");
            var formatter = new DataFormatter();
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            String displayedBudget = new DataFormatter().formatCellValue(
                sheet.getRow(7).getCell(2),
                evaluator
            );
            String displayedZeroActual = formatter.formatCellValue(
                sheet.getRow(7).getCell(3),
                evaluator
            );
            String displayedPercentage = formatter.formatCellValue(
                sheet.getRow(7).getCell(8),
                evaluator
            );

            assertThat(displayedBudget).isEmpty();
            assertThat(displayedZeroActual).isEmpty();
            assertThat(displayedPercentage).isEqualTo("-");
            assertThat(sheet.getRow(7).getCell(8).getCellStyle().getAlignment())
                .isEqualTo(HorizontalAlignment.RIGHT);
        }
    }

    @Test
    void rendersExpenditureWorkbookFromReportMetadata() throws Exception {
        QuarterlyFinancialReport expenditure = report(
            "Expenditure",
            "지출",
            "CONTINGENCY"
        );

        byte[] bytes = service("/branding/church_logo.png").render(expenditure);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Expenditure");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("2026 년도 2분기 지출");
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue())
                .isEqualTo("CONTINGENCY");
            assertThat(workbook.getAllPictures()).hasSize(1);
            assertAdjustTo100Percent(sheet);
            assertQuarterlyColumnLayout(sheet);
        }
    }

    @Test
    void writesRepresentativeWorkbookForVisualInspection() throws Exception {
        Path offeringOutput = Path.of("target", "quarterly-offerings-preview.xlsx");
        Path expenditureOutput = Path.of("target", "quarterly-expenditures-preview.xlsx");

        Files.createDirectories(offeringOutput.getParent());
        Files.write(offeringOutput, service("/branding/church_logo.png").render(report()));
        Files.write(
            expenditureOutput,
            service("/branding/church_logo.png").render(report("Expenditure", "지출", "CONTINGENCY"))
        );

        assertThat(offeringOutput).exists();
        assertThat(expenditureOutput).exists();
        assertThat(Files.size(offeringOutput)).isGreaterThan(0);
        assertThat(Files.size(expenditureOutput)).isGreaterThan(0);
    }

    private QuarterlyFinancialExcelService service(String logoPath) {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Capstone Presbyterian Church",
                "111 Cactus Ave",
                "contact@example.com",
                "Treasurer",
                "1234567890",
                "Toronto, ON",
                "https://example.com"
            ),
            new ChurchInformationProperties.Branding("/branding/banner.png", logoPath),
            new ChurchInformationProperties.Ui(20)
        );
        return new QuarterlyFinancialExcelService(properties);
    }

    private void assertAdjustTo100Percent(XSSFSheet sheet) {
        assertThat(sheet.getPrintSetup().getScale()).isEqualTo((short) 100);
        assertThat(sheet.getCTWorksheet().getSheetPr().getPageSetUpPr().getFitToPage())
            .isFalse();
        assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToWidth()).isFalse();
        assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToHeight()).isFalse();
    }

    private void assertQuarterlyColumnLayout(XSSFSheet sheet) {
        assertThat(sheet.getColumnWidth(0)).isEqualTo(7 * 256);
        assertThat(sheet.getColumnWidth(1)).isEqualTo(28 * 256);
        assertThat(sheet.getColumnWidth(8)).isEqualTo((int) (8.5 * 256));
        assertThat(sheet.getColumnWidth(9)).isEqualTo(16 * 256);
        for (var row : sheet) {
            var cell = row.getCell(1);
            if (cell != null) {
                assertThat(cell.getCellStyle().getWrapText()).isTrue();
            }
        }
    }

    private QuarterlyFinancialReport report() {
        return report("Offering income", "수입", "전년도 이월금");
    }

    private QuarterlyFinancialReport report(
        String sheetName,
        String titleSuffix,
        String specialRowLabel
    ) {
        return new QuarterlyFinancialReport(
            2026,
            2,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30),
            2026,
            LocalDate.of(2026, 1, 1),
            List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
            List.of(
                new QuarterlyFinancialGroup(1, "GENERAL", "General Fund", List.of(
                    row("GENERAL", "General Fund", "TITHE", "Tithe", "12000", "100", "200", "0", "900"),
                    row("GENERAL", "General Fund", "MISSIONS", "Missions", "2400", "50", "0", "75", "300")
                )),
                new QuarterlyFinancialGroup(2, "BUILDING", "Building Fund", List.of(
                    row("BUILDING", "Building Fund", "PROJECT", "Building Project", "0", "0", "100", "0", "400")
                ))
            ),
            new BigDecimal("2500"),
            List.of(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999")),
            new BigDecimal("999"),
            sheetName,
            titleSuffix,
            specialRowLabel
        );
    }

    private QuarterlyFinancialRow row(
        String fundCode,
        String fundLabel,
        String categoryCode,
        String categoryLabel,
        String budget,
        String monthOne,
        String monthTwo,
        String monthThree,
        String cumulative
    ) {
        return new QuarterlyFinancialRow(
            fundCode,
            fundLabel,
            categoryCode,
            categoryLabel,
            new BigDecimal(budget),
            List.of(new BigDecimal(monthOne), new BigDecimal(monthTwo), new BigDecimal(monthThree)),
            new BigDecimal(cumulative)
        );
    }
}
