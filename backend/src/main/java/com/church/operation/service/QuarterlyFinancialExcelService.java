package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.QuarterlyFinancialGroup;
import com.church.operation.dto.QuarterlyFinancialReport;
import com.church.operation.dto.QuarterlyFinancialRow;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.poi.ss.util.CellRangeAddress.valueOf;

@Service
public class QuarterlyFinancialExcelService {
    private static final String[] HEADERS = {
        "구 분", "항 목", "예산", "%d월", "%d월", "%d월", "분기 합계", "누적", "예산대비", "비고"
    };

    private final ChurchInformationProperties properties;

    public QuarterlyFinancialExcelService(ChurchInformationProperties properties) {
        this.properties = properties;
    }

    public byte[] render(QuarterlyFinancialReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(report.sheetName());
            Styles styles = new Styles(workbook);
            configureColumns(sheet);
            createTopRows(sheet, report, styles);
            int finalRow = createReportRows(sheet, report, styles);
            addLogo(workbook, sheet);
            configurePrint(workbook, sheet, finalRow);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create the quarterly financial workbook.", ex);
        }
    }

    private void configureColumns(XSSFSheet sheet) {
        sheet.setColumnWidth(0, (int) (7.83203125 * 256));
        sheet.setColumnWidth(1, (int) (28.5 * 256));
        for (int column = 2; column <= 7; column++) {
            sheet.setColumnWidth(column, (int) (10.5 * 256));
        }
        sheet.setColumnWidth(8, (int) (9.33203125 * 256));
        sheet.setColumnWidth(9, (int) (16.5 * 256));
    }

    private void createTopRows(XSSFSheet sheet, QuarterlyFinancialReport report, Styles styles) {
        Row logoRow = sheet.createRow(0);
        logoRow.setHeightInPoints(45);
        createCells(logoRow, styles.blank());

        Row titleRow = sheet.createRow(1);
        titleRow.setHeightInPoints(23);
        createCells(titleRow, styles.title());
        titleRow.getCell(0).setCellValue(
            report.calendarYear() + " 년도 " + report.quarter() + "분기 " + report.titleSuffix()
        );
        sheet.addMergedRegion(valueOf("A2:J2"));

        Row blank = sheet.createRow(2);
        createCells(blank, styles.blank());

        Row header = sheet.createRow(3);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellStyle(styles.header());
            if (column >= 3 && column <= 5) {
                cell.setCellValue(String.format(
                    HEADERS[column],
                    report.months().get(column - 3).getMonthValue()
                ));
            } else {
                cell.setCellValue(HEADERS[column]);
            }
        }
    }

    private int createReportRows(
        XSSFSheet sheet,
        QuarterlyFinancialReport report,
        Styles styles
    ) {
        int rowIndex = 4;
        List<Integer> subtotalRows = new ArrayList<>();
        for (QuarterlyFinancialGroup group : report.groups()) {
            int firstDetailRow = rowIndex;
            for (QuarterlyFinancialRow item : group.rows()) {
                Row row = sheet.createRow(rowIndex);
                createCells(row, styles.body());
                if (rowIndex == firstDetailRow) {
                    row.getCell(0).setCellValue(group.groupLabel());
                    row.getCell(0).setCellStyle(styles.fund());
                }
                row.getCell(1).setCellValue(item.itemLabel());
                // enable wrap text for the item label cell
                row.getCell(1).getCellStyle().setWrapText(true);
                numeric(row.getCell(2), item.budget(), styles.currency());
                for (int month = 0; month < 3; month++) {
                    numeric(row.getCell(3 + month), item.monthlyActuals().get(month), styles.currency());
                }
                row.getCell(6).setCellFormula("SUM(D" + (rowIndex + 1) + ":F" + (rowIndex + 1) + ")");
                row.getCell(6).setCellStyle(styles.currency());
                numeric(row.getCell(7), item.cumulativeActual(), styles.currency());
                row.getCell(8).setCellFormula(percentageFormula(rowIndex));
                row.getCell(8).setCellStyle(styles.percentage());
                rowIndex++;
            }

            int subtotalRow = rowIndex;
            Row subtotal = sheet.createRow(subtotalRow);
            createCells(subtotal, styles.subtotal());
            subtotal.getCell(1).setCellValue("(" + group.sequence() + ") 소 계");
            for (int column = 2; column <= 7; column++) {
                subtotal.getCell(column).setCellFormula(
                    "SUM(" + columnLetter(column) + (firstDetailRow + 1)
                        + ":" + columnLetter(column) + subtotalRow + ")"
                );
                subtotal.getCell(column).setCellStyle(styles.subtotalCurrency());
            }
            subtotal.getCell(8).setCellFormula(percentageFormula(subtotalRow));
            subtotal.getCell(8).setCellStyle(styles.subtotalPercentage());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                firstDetailRow,
                subtotalRow,
                0,
                0
            ));
            subtotalRows.add(subtotalRow);
            rowIndex++;
        }

        int combinedRow = rowIndex++;
        Row combined = sheet.createRow(combinedRow);
        createCells(combined, styles.total());
        combined.getCell(0).setCellValue(combinedLabel(report.groups()));
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(combinedRow, combinedRow, 0, 1));
        for (int column = 2; column <= 7; column++) {
            combined.getCell(column).setCellFormula(sumCells(column, subtotalRows));
            combined.getCell(column).setCellStyle(styles.totalCurrency());
        }
        combined.getCell(8).setCellFormula(percentageFormula(combinedRow));
        combined.getCell(8).setCellStyle(styles.totalPercentage());

        int carryRow = rowIndex++;
        Row carry = sheet.createRow(carryRow);
        createCells(carry, styles.total());
        carry.getCell(0).setCellValue(report.specialRowLabel());
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(carryRow, carryRow, 0, 1));
        numeric(carry.getCell(2), report.specialBudget(), styles.totalCurrency());
        for (int month = 0; month < 3; month++) {
            numeric(
                carry.getCell(3 + month),
                listValue(report.specialMonthlyActuals(), month),
                styles.totalCurrency()
            );
        }
        carry.getCell(6).setCellFormula("SUM(D" + (carryRow + 1) + ":F" + (carryRow + 1) + ")");
        carry.getCell(6).setCellStyle(styles.totalCurrency());
        numeric(carry.getCell(7), report.specialCumulativeActual(), styles.totalCurrency());
        carry.getCell(8).setCellFormula(percentageFormula(carryRow));
        carry.getCell(8).setCellStyle(styles.totalPercentage());

        int finalRow = rowIndex;
        Row total = sheet.createRow(finalRow);
        createCells(total, styles.total());
        total.getCell(0).setCellValue("총 합 계");
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(finalRow, finalRow, 0, 1));
        for (int column = 2; column <= 7; column++) {
            total.getCell(column).setCellFormula(
                columnLetter(column) + (combinedRow + 1)
                    + "+" + columnLetter(column) + (carryRow + 1)
            );
            total.getCell(column).setCellStyle(styles.totalCurrency());
        }
        total.getCell(8).setCellFormula(percentageFormula(finalRow));
        total.getCell(8).setCellStyle(styles.totalPercentage());
        return finalRow;
    }

    private void addLogo(XSSFWorkbook workbook, XSSFSheet sheet) {
        byte[] imageBytes = loadLogo();
        if (imageBytes == null) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return;
            }
            int pictureType = isJpeg(imageBytes) ? Workbook.PICTURE_TYPE_JPEG : Workbook.PICTURE_TYPE_PNG;
            int pictureIndex = workbook.addPicture(imageBytes, pictureType);
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            double targetWidth = columnPixels(sheet, 7) + columnPixels(sheet, 8) + columnPixels(sheet, 9);
            double targetHeight = sheet.getRow(0).getHeightInPoints() * 96d / 72d;
            double scale = Math.min(targetWidth / image.getWidth(), targetHeight / image.getHeight());
            double renderedWidth = image.getWidth() * scale;
            double renderedHeight = image.getHeight() * scale;
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(7);
            anchor.setRow1(0);
            anchor.setDx1(Units.pixelToEMU((int) Math.round(targetWidth - renderedWidth)));
            anchor.setDy1(Units.pixelToEMU((int) Math.round(targetHeight - renderedHeight)));
            anchor.setCol2(10);
            anchor.setRow2(1);
            drawing.createPicture(anchor, pictureIndex);
        } catch (IOException | RuntimeException ignored) {
            // Branding is optional; preserve a usable workbook when an image cannot be decoded.
        }
    }

    private byte[] loadLogo() {
        String path = properties.branding() == null ? null : properties.branding().logPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replaceFirst("^/", "");
        for (String candidate : List.of(normalized, "static/" + normalized)) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    return resource.getContentAsByteArray();
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Try the alternate classpath form.
            }
        }
        return null;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length > 2
            && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8;
    }

    private double columnPixels(XSSFSheet sheet, int column) {
        return (sheet.getColumnWidth(column) / 256d) * 7d + 5d;
    }

    private void configurePrint(XSSFWorkbook workbook, XSSFSheet sheet, int finalRow) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.LETTER_PAPERSIZE);
        sheet.getPrintSetup().setScale((short) 100);
        sheet.setFitToPage(false);
        sheet.setAutobreaks(false);
        if (sheet.getCTWorksheet().getPageSetup().isSetFitToWidth()) {
            sheet.getCTWorksheet().getPageSetup().unsetFitToWidth();
        }
        if (sheet.getCTWorksheet().getPageSetup().isSetFitToHeight()) {
            sheet.getCTWorksheet().getPageSetup().unsetFitToHeight();
        }
        sheet.setHorizontallyCenter(true);
        sheet.setMargin(Sheet.TopMargin, 0.5);
        sheet.setMargin(Sheet.BottomMargin, 0.5);
        sheet.setMargin(Sheet.LeftMargin, 0.25);
        sheet.setMargin(Sheet.RightMargin, 0.25);
        sheet.getFooter().setCenter("Page &P");
        workbook.setPrintArea(0, 0, 9, 0, finalRow);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:4"));
    }

    private void createCells(Row row, CellStyle style) {
        for (int column = 0; column < 10; column++) {
            Cell cell = row.createCell(column);
            cell.setCellStyle(style);
        }
    }

    private void numeric(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private BigDecimal listValue(List<BigDecimal> values, int index) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) {
            return BigDecimal.ZERO;
        }
        return values.get(index);
    }

    private String percentageFormula(int zeroBasedRow) {
        int row = zeroBasedRow + 1;
        return "IF(C" + row + "=0,\"-\",H" + row + "/C" + row + ")";
    }

    private String combinedLabel(List<QuarterlyFinancialGroup> groups) {
        if (groups.isEmpty()) {
            return "합 계";
        }
        return groups.stream()
            .map(group -> "(" + group.sequence() + ")")
            .reduce((left, right) -> left + " + " + right)
            .orElse("") + " 합 계";
    }

    private String sumCells(int column, List<Integer> rows) {
        if (rows.isEmpty()) {
            return "0";
        }
        return "SUM(" + rows.stream()
            .map(row -> columnLetter(column) + (row + 1))
            .reduce((left, right) -> left + "," + right)
            .orElse("") + ")";
    }

    private String columnLetter(int zeroBasedColumn) {
        return String.valueOf((char) ('A' + zeroBasedColumn));
    }

    private static final class Styles {
        private final CellStyle body;
        private final CellStyle blank;
        private final CellStyle fund;
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle currency;
        private final CellStyle percentage;
        private final CellStyle subtotal;
        private final CellStyle subtotalCurrency;
        private final CellStyle subtotalPercentage;
        private final CellStyle total;
        private final CellStyle totalCurrency;
        private final CellStyle totalPercentage;

        private Styles(XSSFWorkbook workbook) {
            Font bodyFont = font(workbook, 12, false, false);
            Font boldFont = font(workbook, 12, true, false);
            Font titleFont = font(workbook, 18, true, true);

            body = bordered(workbook, bodyFont);
            body.setVerticalAlignment(VerticalAlignment.CENTER);

            blank = workbook.createCellStyle();
            blank.setFont(bodyFont);

            fund = clone(workbook, body);
            fund.setAlignment(HorizontalAlignment.CENTER);
            fund.setVerticalAlignment(VerticalAlignment.CENTER);
            fund.setWrapText(true);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            header = bordered(workbook, boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            currency = clone(workbook, body);
            currency.setDataFormat(workbook.createDataFormat().getFormat(
                "\"$\"#,##0;[Red](\"$\"#,##0);\"\""
            ));
            percentage = clone(workbook, body);
            percentage.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
            percentage.setAlignment(HorizontalAlignment.RIGHT);

            subtotal = bordered(workbook, boldFont);
            subtotalCurrency = clone(workbook, subtotal);
            subtotalCurrency.setDataFormat(currency.getDataFormat());
            subtotalPercentage = clone(workbook, subtotal);
            subtotalPercentage.setDataFormat(percentage.getDataFormat());

            total = bordered(workbook, boldFont);
            totalCurrency = clone(workbook, total);
            totalCurrency.setDataFormat(currency.getDataFormat());
            totalPercentage = clone(workbook, total);
            totalPercentage.setDataFormat(percentage.getDataFormat());
        }

        private static Font font(XSSFWorkbook workbook, int size, boolean bold, boolean underline) {
            Font font = workbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            if (underline) {
                font.setUnderline(Font.U_SINGLE);
            }
            return font;
        }

        private static XSSFCellStyle bordered(XSSFWorkbook workbook, Font font) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static XSSFCellStyle clone(XSSFWorkbook workbook, CellStyle source) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(source);
            return style;
        }

        private CellStyle body() { return body; }
        private CellStyle blank() { return blank; }
        private CellStyle fund() { return fund; }
        private CellStyle title() { return title; }
        private CellStyle header() { return header; }
        private CellStyle currency() { return currency; }
        private CellStyle percentage() { return percentage; }
        private CellStyle subtotal() { return subtotal; }
        private CellStyle subtotalCurrency() { return subtotalCurrency; }
        private CellStyle subtotalPercentage() { return subtotalPercentage; }
        private CellStyle total() { return total; }
        private CellStyle totalCurrency() { return totalCurrency; }
        private CellStyle totalPercentage() { return totalPercentage; }
    }
}
