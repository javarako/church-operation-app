package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.dto.YearlyFinancialGroup;
import com.church.operation.dto.YearlyFinancialReport;
import com.church.operation.dto.YearlyFinancialRow;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class YearlyFinancialExcelService {
    private static final double[] COLUMN_WIDTHS = {
        7.83203125, 28.83203125, 12.83203125, 12.83203125,
        8.83203125, 12.83203125, 8.83203125, 32.83203125
    };

    private final ChurchInformationProperties properties;

    public YearlyFinancialExcelService(ChurchInformationProperties properties) {
        this.properties = properties;
    }

    public byte[] render(YearlyFinancialReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(report.sheetName());
            Styles styles = new Styles(workbook);
            configureColumns(sheet);
            createTopRows(sheet, report, styles);
            int finalRow = createReportRows(sheet, report, styles);
            FinancialExcelLayoutSupport.addLogo(workbook, sheet, properties, 6, 8);
            FinancialExcelLayoutSupport.configurePrint(workbook, sheet, 7, finalRow);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the yearly financial workbook.", exception);
        }
    }

    private void configureColumns(XSSFSheet sheet) {
        for (int column = 0; column < COLUMN_WIDTHS.length; column++) {
            sheet.setColumnWidth(column, (int) (COLUMN_WIDTHS[column] * 256));
        }
    }

    private void createTopRows(XSSFSheet sheet, YearlyFinancialReport report, Styles styles) {
        Row logoRow = sheet.createRow(0);
        logoRow.setHeightInPoints(45);
        createCells(logoRow, styles.blank());

        Row titleRow = sheet.createRow(1);
        titleRow.setHeightInPoints(23);
        createCells(titleRow, styles.title());
        titleRow.getCell(0).setCellValue(report.fiscalYear() + "년도 " + report.titleSuffix());
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 7));

        Row blankRow = sheet.createRow(2);
        createCells(blankRow, styles.blank());

        String[] headers = {
            "구 분",
            "항 목",
            report.fiscalYear() + " 예산",
            report.actualHeader(),
            report.actualRatioHeader(),
            (report.fiscalYear() + 1) + " 예산",
            "예산대비",
            "비고"
        };
        Row headerRow = sheet.createRow(3);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = headerRow.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(styles.header());
        }
    }

    private int createReportRows(
        XSSFSheet sheet,
        YearlyFinancialReport report,
        Styles styles
    ) {
        int rowIndex = 4;
        List<Integer> subtotalRows = new ArrayList<>();
        for (YearlyFinancialGroup group : report.groups()) {
            int firstDetailRow = rowIndex;
            for (YearlyFinancialRow item : group.rows()) {
                Row row = sheet.createRow(rowIndex);
                createCells(row, styles.body());
                if (rowIndex == firstDetailRow) {
                    row.getCell(0).setCellValue(group.groupLabel());
                    row.getCell(0).setCellStyle(styles.group());
                }
                row.getCell(1).setCellValue(item.itemLabel());
                row.getCell(1).getCellStyle().setWrapText(true);
                numeric(row.getCell(2), item.currentBudget(), styles.currency());
                numeric(row.getCell(3), item.actual(), styles.currency());
                ratio(row.getCell(4), actualRatioFormula(rowIndex), styles.percentage());
                nextBudget(row.getCell(5), item.nextBudget(), item.nextBudgetPresent(), styles.currency());
                ratio(row.getCell(6), nextRatioFormula(rowIndex), styles.percentage());
                rowIndex++;
            }

            int subtotalRow = rowIndex;
            Row subtotal = sheet.createRow(subtotalRow);
            createCells(subtotal, styles.subtotal());
            subtotal.getCell(1).setCellValue("(" + group.sequence() + ") 소 계");
            sumRange(subtotal.getCell(2), 2, firstDetailRow, subtotalRow - 1, styles.subtotalCurrency());
            sumRange(subtotal.getCell(3), 3, firstDetailRow, subtotalRow - 1, styles.subtotalCurrency());
            ratio(subtotal.getCell(4), actualRatioFormula(subtotalRow), styles.subtotalPercentage());
            conditionalSumRange(
                subtotal.getCell(5),
                5,
                firstDetailRow,
                subtotalRow - 1,
                styles.subtotalCurrency()
            );
            ratio(subtotal.getCell(6), nextRatioFormula(subtotalRow), styles.subtotalPercentage());
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
        sumCells(combined.getCell(2), 2, subtotalRows, styles.totalCurrency());
        sumCells(combined.getCell(3), 3, subtotalRows, styles.totalCurrency());
        ratio(combined.getCell(4), actualRatioFormula(combinedRow), styles.totalPercentage());
        conditionalSumCells(combined.getCell(5), 5, subtotalRows, styles.totalCurrency());
        ratio(combined.getCell(6), nextRatioFormula(combinedRow), styles.totalPercentage());

        int specialRow = rowIndex++;
        Row special = sheet.createRow(specialRow);
        createCells(special, styles.total());
        special.getCell(0).setCellValue(report.specialRowLabel());
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(specialRow, specialRow, 0, 1));
        numeric(special.getCell(2), report.specialCurrentBudget(), styles.totalCurrency());
        numeric(special.getCell(3), report.specialActual(), styles.totalCurrency());
        ratio(special.getCell(4), actualRatioFormula(specialRow), styles.totalPercentage());
        nextBudget(
            special.getCell(5),
            report.specialNextBudget(),
            report.specialNextBudgetPresent(),
            styles.totalCurrency()
        );
        ratio(special.getCell(6), nextRatioFormula(specialRow), styles.totalPercentage());

        int finalRow = rowIndex;
        Row total = sheet.createRow(finalRow);
        createCells(total, styles.total());
        total.getCell(0).setCellValue("총 합 계");
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(finalRow, finalRow, 0, 1));
        sumPair(total.getCell(2), 2, combinedRow, specialRow, styles.totalCurrency());
        sumPair(total.getCell(3), 3, combinedRow, specialRow, styles.totalCurrency());
        ratio(total.getCell(4), actualRatioFormula(finalRow), styles.totalPercentage());
        conditionalSumPair(total.getCell(5), 5, combinedRow, specialRow, styles.totalCurrency());
        ratio(total.getCell(6), nextRatioFormula(finalRow), styles.totalPercentage());
        return finalRow;
    }

    private void numeric(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void nextBudget(Cell cell, BigDecimal value, boolean present, CellStyle style) {
        if (present) {
            numeric(cell, value, style);
        } else {
            cell.setCellValue("-");
            cell.setCellStyle(style);
        }
    }

    private void ratio(Cell cell, String formula, CellStyle style) {
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private void sumRange(
        Cell cell,
        int column,
        int firstRow,
        int lastRow,
        CellStyle style
    ) {
        cell.setCellFormula("SUM(" + cellReference(column, firstRow) + ":" + cellReference(column, lastRow) + ")");
        cell.setCellStyle(style);
    }

    private void conditionalSumRange(
        Cell cell,
        int column,
        int firstRow,
        int lastRow,
        CellStyle style
    ) {
        String range = cellReference(column, firstRow) + ":" + cellReference(column, lastRow);
        cell.setCellFormula("IF(COUNT(" + range + ")=0,\"-\",SUM(" + range + "))");
        cell.setCellStyle(style);
    }

    private void sumCells(Cell cell, int column, List<Integer> rows, CellStyle style) {
        cell.setCellFormula("SUM(" + references(column, rows) + ")");
        cell.setCellStyle(style);
    }

    private void conditionalSumCells(Cell cell, int column, List<Integer> rows, CellStyle style) {
        String references = references(column, rows);
        cell.setCellFormula("IF(COUNT(" + references + ")=0,\"-\",SUM(" + references + "))");
        cell.setCellStyle(style);
    }

    private void sumPair(
        Cell cell,
        int column,
        int firstRow,
        int secondRow,
        CellStyle style
    ) {
        String references = cellReference(column, firstRow) + "," + cellReference(column, secondRow);
        cell.setCellFormula("SUM(" + references + ")");
        cell.setCellStyle(style);
    }

    private void conditionalSumPair(
        Cell cell,
        int column,
        int firstRow,
        int secondRow,
        CellStyle style
    ) {
        String references = cellReference(column, firstRow) + "," + cellReference(column, secondRow);
        cell.setCellFormula("IF(COUNT(" + references + ")=0,\"-\",SUM(" + references + "))");
        cell.setCellStyle(style);
    }

    private String actualRatioFormula(int zeroBasedRow) {
        int row = zeroBasedRow + 1;
        return "IF(C" + row + "=0,\"-\",D" + row + "/C" + row + ")";
    }

    private String nextRatioFormula(int zeroBasedRow) {
        int row = zeroBasedRow + 1;
        return "IF(OR(C" + row + "=0,NOT(ISNUMBER(F" + row + "))),\"-\",F" + row + "/C" + row + ")";
    }

    private String combinedLabel(List<YearlyFinancialGroup> groups) {
        if (groups.isEmpty()) {
            return "합 계";
        }
        return groups.stream()
            .map(group -> "(" + group.sequence() + ")")
            .reduce((left, right) -> left + " + " + right)
            .orElse("") + " 합 계";
    }

    private String references(int column, List<Integer> rows) {
        if (rows.isEmpty()) {
            return "0";
        }
        return rows.stream()
            .map(row -> cellReference(column, row))
            .reduce((left, right) -> left + "," + right)
            .orElse("0");
    }

    private String cellReference(int zeroBasedColumn, int zeroBasedRow) {
        return String.valueOf((char) ('A' + zeroBasedColumn)) + (zeroBasedRow + 1);
    }

    private void createCells(Row row, CellStyle style) {
        for (int column = 0; column < 8; column++) {
            Cell cell = row.createCell(column);
            cell.setCellStyle(style);
        }
    }

    private static final class Styles {
        private final CellStyle body;
        private final CellStyle blank;
        private final CellStyle group;
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

            group = clone(workbook, body);
            group.setAlignment(HorizontalAlignment.CENTER);
            group.setVerticalAlignment(VerticalAlignment.CENTER);
            group.setWrapText(true);

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
            subtotalPercentage.setAlignment(HorizontalAlignment.RIGHT);

            total = bordered(workbook, boldFont);
            totalCurrency = clone(workbook, total);
            totalCurrency.setDataFormat(currency.getDataFormat());
            totalPercentage = clone(workbook, total);
            totalPercentage.setDataFormat(percentage.getDataFormat());
            totalPercentage.setAlignment(HorizontalAlignment.RIGHT);
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
        private CellStyle group() { return group; }
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
