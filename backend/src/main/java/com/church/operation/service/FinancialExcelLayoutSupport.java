package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

final class FinancialExcelLayoutSupport {
    private FinancialExcelLayoutSupport() {
    }

    static void addLogo(
        XSSFWorkbook workbook,
        XSSFSheet sheet,
        ChurchInformationProperties properties,
        int firstColumn,
        int lastColumnExclusive
    ) {
        byte[] imageBytes = loadLogo(properties);
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
            double targetWidth = 0;
            for (int column = firstColumn; column < lastColumnExclusive; column++) {
                targetWidth += columnPixels(sheet, column);
            }
            double targetHeight = sheet.getRow(0).getHeightInPoints() * 96d / 72d;
            double scale = Math.min(targetWidth / image.getWidth(), targetHeight / image.getHeight());
            double renderedWidth = image.getWidth() * scale;
            double renderedHeight = image.getHeight() * scale;
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(firstColumn);
            anchor.setRow1(0);
            anchor.setDx1(Units.pixelToEMU((int) Math.round(targetWidth - renderedWidth)));
            anchor.setDy1(Units.pixelToEMU((int) Math.round(targetHeight - renderedHeight)));
            anchor.setCol2(lastColumnExclusive);
            anchor.setRow2(1);
            drawing.createPicture(anchor, pictureIndex);
        } catch (IOException | RuntimeException ignored) {
            // Branding is optional; preserve a usable workbook when an image cannot be decoded.
        }
    }

    static void configurePrint(
        XSSFWorkbook workbook,
        XSSFSheet sheet,
        int lastColumn,
        int finalRow
    ) {
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
        workbook.setPrintArea(0, 0, lastColumn, 0, finalRow);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:4"));
    }

    private static byte[] loadLogo(ChurchInformationProperties properties) {
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

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 2
            && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8;
    }

    private static double columnPixels(XSSFSheet sheet, int column) {
        return (sheet.getColumnWidth(column) / 256d) * 7d + 5d;
    }
}
