package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.YearlyFinancialGroup;
import com.church.operation.dto.YearlyFinancialReport;
import com.church.operation.dto.YearlyFinancialRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YearlyFinancialExcelServiceTest {
    @Test
    void rendersOfferingTitleHeadersRowsAndFormulas() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(offeringReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Offering income");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("2026년도 수입 결산 및 예산안");
            assertThat(rowValues(sheet, 3)).containsExactly(
                "구 분", "항 목", "2026 예산", "수입결산", "수입대비",
                "2027 예산", "예산대비", "비고"
            );

            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("General Fund");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Tithe");
            assertThat(sheet.getRow(4).getCell(4).getCellFormula())
                .isEqualTo("IF(C5=0,\"-\",D5/C5)");
            assertThat(sheet.getRow(4).getCell(6).getCellFormula())
                .isEqualTo("IF(OR(C5=0,NOT(ISNUMBER(F5))),\"-\",F5/C5)");

            assertThat(sheet.getRow(4).getCell(5).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(4).getCell(5).getStringCellValue()).isEqualTo("-");
            assertThat(sheet.getRow(5).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(5).getCell(5).getNumericCellValue()).isZero();

            assertThat(sheet.getRow(6).getCell(1).getStringCellValue()).isEqualTo("(1) 소 계");
            assertThat(sheet.getRow(8).getCell(1).getStringCellValue()).isEqualTo("(2) 소 계");
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("(1) + (2) 합 계");
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("전년도 이월금");
            assertThat(sheet.getRow(11).getCell(0).getStringCellValue()).isEqualTo("총 합 계");
            assertThat(sheet.getRow(11).getCell(5).getCellFormula())
                .isEqualTo("IF(COUNT(F10,F11)=0,\"-\",SUM(F10,F11))");

            assertThat(sheet.getMergedRegions()).contains(
                new CellRangeAddress(1, 1, 0, 7),
                new CellRangeAddress(4, 6, 0, 0),
                new CellRangeAddress(7, 8, 0, 0),
                new CellRangeAddress(9, 9, 0, 1),
                new CellRangeAddress(10, 10, 0, 1),
                new CellRangeAddress(11, 11, 0, 1)
            );
        }
    }

    @Test
    void rendersExpenditureMetadataAndMissingSpecialNextBudget() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(expenditureReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Expenditure");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("2026년도 지출 결산 및 예산안");
            assertThat(rowValues(sheet, 3)).containsExactly(
                "구 분", "항 목", "2026 예산", "지출결산", "지출대비",
                "2027 예산", "예산대비", "비고"
            );
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("CONTINGENCY");
            assertThat(sheet.getRow(10).getCell(5).getStringCellValue()).isEqualTo("-");
            assertThat(displayed(sheet, 10, 6)).isEqualTo("-");
        }
    }

    @Test
    void calculatesTotalsAndDisplaysZeroValuesLikeTheSample() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(offeringReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Offering income");
            assertThat(displayed(sheet, 4, 2)).isEqualTo("$12,000");
            assertThat(displayed(sheet, 7, 2)).isEmpty();
            assertThat(displayed(sheet, 7, 4)).isEqualTo("-");
            assertThat(displayed(sheet, 4, 6)).isEqualTo("-");
            assertThat(displayed(sheet, 5, 6)).isEqualTo("0.00%");
            assertThat(sheet.getRow(4).getCell(6).getCellStyle().getAlignment())
                .isEqualTo(HorizontalAlignment.RIGHT);
        }
    }

    @Test
    void embedsLogoAndAppliesSampleColumnAndPrintLayout() throws Exception {
        byte[] bytes = service("/branding/church_logo.png").render(offeringReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Offering income");
            assertThat(workbook.getAllPictures()).hasSize(1);
            XSSFPicture picture = (XSSFPicture) sheet.getDrawingPatriarch().getShapes().getFirst();
            assertThat(picture.getClientAnchor().getCol1()).isEqualTo((short) 6);
            assertThat(picture.getClientAnchor().getCol2()).isEqualTo((short) 8);
            assertThat(picture.getClientAnchor().getRow1()).isZero();
            assertThat(picture.getClientAnchor().getRow2()).isEqualTo(1);

            double[] widths = {
                7.83203125, 28.83203125, 12.83203125, 12.83203125,
                8.83203125, 12.83203125, 8.83203125, 32.83203125
            };
            for (int column = 0; column < widths.length; column++) {
                assertThat(sheet.getColumnWidth(column)).isEqualTo((int) (widths[column] * 256));
            }
            assertThat(sheet.getRow(4).getCell(1).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getPrintSetup().getScale()).isEqualTo((short) 100);
            assertThat(sheet.getCTWorksheet().getSheetPr().getPageSetUpPr().getFitToPage()).isFalse();
            assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToWidth()).isFalse();
            assertThat(sheet.getCTWorksheet().getPageSetup().isSetFitToHeight()).isFalse();
            assertThat(sheet.getHorizontallyCenter()).isTrue();
            assertThat(sheet.getMargin(Sheet.TopMargin)).isEqualTo(0.5);
            assertThat(sheet.getMargin(Sheet.BottomMargin)).isEqualTo(0.5);
            assertThat(sheet.getMargin(Sheet.LeftMargin)).isEqualTo(0.25);
            assertThat(sheet.getMargin(Sheet.RightMargin)).isEqualTo(0.25);
            assertThat(sheet.getFooter().getCenter()).isEqualTo("Page &P");
            assertThat(sheet.getRepeatingRows()).isEqualTo(CellRangeAddress.valueOf("1:4"));
            assertThat(workbook.getPrintArea(0)).contains("$A$1:$H$12");

            Font titleFont = workbook.getFontAt(sheet.getRow(1).getCell(0).getCellStyle().getFontIndex());
            assertThat(titleFont.getFontName()).isEqualTo("Arial");
            assertThat(titleFont.getFontHeightInPoints()).isEqualTo((short) 18);
            assertThat(titleFont.getBold()).isTrue();
            assertThat(titleFont.getUnderline()).isEqualTo(Font.U_SINGLE);
        }
    }

    @Test
    void rendersWithoutAnAvailableLogoAndWritesPreviewFiles() throws Exception {
        byte[] withoutLogo = service("/branding/not-found.png").render(offeringReport());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(withoutLogo))) {
            assertThat(workbook.getAllPictures()).isEmpty();
        }

        Path offeringOutput = Path.of("target", "yearly-offerings-preview.xlsx");
        Path expenditureOutput = Path.of("target", "yearly-expenditures-preview.xlsx");
        Files.createDirectories(offeringOutput.getParent());
        Files.write(offeringOutput, service("/branding/church_logo.png").render(offeringReport()));
        Files.write(expenditureOutput, service("/branding/church_logo.png").render(expenditureReport()));
        assertThat(Files.size(offeringOutput)).isGreaterThan(0);
        assertThat(Files.size(expenditureOutput)).isGreaterThan(0);
    }

    private List<String> rowValues(XSSFSheet sheet, int rowIndex) {
        return java.util.stream.IntStream.range(0, 8)
            .mapToObj(column -> sheet.getRow(rowIndex).getCell(column).getStringCellValue())
            .toList();
    }

    private String displayed(XSSFSheet sheet, int row, int column) {
        XSSFWorkbook workbook = sheet.getWorkbook();
        return new DataFormatter().formatCellValue(
            sheet.getRow(row).getCell(column),
            workbook.getCreationHelper().createFormulaEvaluator()
        );
    }

    private YearlyFinancialExcelService service(String logoPath) {
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
        return new YearlyFinancialExcelService(properties);
    }

    private YearlyFinancialReport offeringReport() {
        return report("Offering income", "수입 결산 및 예산안", "수입결산", "수입대비", "전년도 이월금", true);
    }

    private YearlyFinancialReport expenditureReport() {
        return report("Expenditure", "지출 결산 및 예산안", "지출결산", "지출대비", "CONTINGENCY", false);
    }

    private YearlyFinancialReport report(
        String sheetName,
        String titleSuffix,
        String actualHeader,
        String actualRatioHeader,
        String specialLabel,
        boolean specialNextPresent
    ) {
        return new YearlyFinancialReport(
            2026,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            List.of(
                new YearlyFinancialGroup(1, "GENERAL", "General Fund", List.of(
                    row("GENERAL", "General Fund", "TITHE", "Tithe", "12000", "900", "0", false),
                    row("GENERAL", "General Fund", "MISSIONS", "Missions", "2400", "300", "0", true)
                )),
                new YearlyFinancialGroup(2, "BUILDING", "Building Fund", List.of(
                    row("BUILDING", "Building Fund", "PROJECT", "Building Project", "0", "400", "3000", true)
                ))
            ),
            new BigDecimal("2500"),
            new BigDecimal("999"),
            new BigDecimal("2750"),
            specialNextPresent,
            sheetName,
            titleSuffix,
            actualHeader,
            actualRatioHeader,
            specialLabel
        );
    }

    private YearlyFinancialRow row(
        String groupCode,
        String groupLabel,
        String itemCode,
        String itemLabel,
        String currentBudget,
        String actual,
        String nextBudget,
        boolean nextBudgetPresent
    ) {
        return new YearlyFinancialRow(
            groupCode,
            groupLabel,
            itemCode,
            itemLabel,
            new BigDecimal(currentBudget),
            new BigDecimal(actual),
            new BigDecimal(nextBudget),
            nextBudgetPresent
        );
    }
}
