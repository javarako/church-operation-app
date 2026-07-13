package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.TaxReceipt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TaxReceiptPdfService {
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float HALF_HEIGHT = PDRectangle.LETTER.getHeight() / 2f;
    private static final float LEFT = 30f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 60f;
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final ChurchInformationProperties properties;

    public TaxReceiptPdfService(ChurchInformationProperties properties) {
        this.properties = properties;
    }

    public byte[] render(TaxReceipt receipt) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDImageXObject logo = loadLogo(document);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                renderReceipt(stream, receipt, PDRectangle.LETTER.getHeight(), logo);
                stream.setLineDashPattern(new float[]{5f, 4f}, 0f);
                stream.setStrokingColor(125f / 255f);
                stream.moveTo(18f, HALF_HEIGHT);
                stream.lineTo(PAGE_WIDTH - 18f, HALF_HEIGHT);
                stream.stroke();
                stream.setLineDashPattern(new float[]{}, 0f);
                renderReceipt(stream, receipt, HALF_HEIGHT, logo);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to render the official tax receipt PDF.", ex);
        }
    }

    private void renderReceipt(PDPageContentStream stream, TaxReceipt receipt, float originY, PDImageXObject logo)
        throws IOException {
        float titleY = originY - 22f;
        writeCentered(stream, "Official Receipt for Income Tax Purposes", titleY, BOLD, 12f);

        if (logo != null) {
            float scale = Math.min(42f / logo.getWidth(), 42f / logo.getHeight());
            stream.drawImage(logo, LEFT, originY - 72f, logo.getWidth() * scale, logo.getHeight() * scale);
        }
        float churchX = logo == null ? LEFT : 82f;
        write(stream, receipt.getChurchName(), churchX, originY - 43f, BOLD, 10f);
        writeFit(stream, receipt.getChurchAddress(), churchX, originY - 56f, PAGE_WIDTH - churchX - 30f, REGULAR, 7.5f);
        write(stream, "Charity registration: " + value(receipt.getCharityRegistrationNumber()), churchX, originY - 68f, REGULAR, 7.5f);
        writeFit(stream, value(receipt.getChurchWebsite()), churchX, originY - 80f, PAGE_WIDTH - churchX - 30f, REGULAR, 7.5f);

        write(stream, "Receipt number: " + value(receipt.getReceiptNumber()), LEFT, originY - 101f, BOLD, 8.5f);
        write(stream, "Receipt issued: " + value(receipt.getIssueDate()), LEFT, originY - 114f, REGULAR, 8f);
        write(stream, "Issued at: " + value(receipt.getReceiptIssueLocation()), LEFT, originY - 127f, REGULAR, 8f);
        write(stream, "Donations received during: " + receipt.getTaxYear(), 330f, originY - 101f, REGULAR, 8f);
        write(stream, "Offering number: " + value(receipt.getOfferingNumber()), 330f, originY - 114f, REGULAR, 8f);

        write(stream, "Donor: " + value(receipt.getDonorName()), LEFT, originY - 150f, BOLD, 8.5f);
        writeFit(stream, value(receipt.getDonorAddress()), LEFT, originY - 164f, CONTENT_WIDTH, REGULAR, 8f);
        write(stream, "Amount of gift: " + money(receipt.getGiftAmount()), LEFT, originY - 187f, BOLD, 9f);
        write(stream, "Advantage amount: " + money(receipt.getAdvantageAmount()), 220f, originY - 187f, REGULAR, 8f);
        write(stream, "Advantage description: " + value(receipt.getAdvantageDescription()), 390f, originY - 187f, REGULAR, 8f);
        write(stream, "Eligible amount: " + money(receipt.getEligibleAmount()), LEFT, originY - 202f, BOLD, 9f);

        write(stream, "Thank you", LEFT, originY - 225f, BOLD, 8f);
        writeWrapped(stream, value(receipt.getThankYouNote()), LEFT, originY - 238f, CONTENT_WIDTH, REGULAR, 7f, 9f, 3);

        float signatureY = originY - 290f;
        stream.setStrokingColor(45f / 255f);
        stream.moveTo(LEFT, signatureY);
        stream.lineTo(220f, signatureY);
        stream.stroke();
        write(stream, value(receipt.getTreasurerName()) + ", Treasurer", LEFT, signatureY - 12f, REGULAR, 7.5f);
        write(stream, "Authorized signature", LEFT, signatureY + 4f, REGULAR, 6.5f);
        write(stream, "Canada Revenue Agency", 330f, signatureY - 1f, BOLD, 7.5f);
        write(stream, "canada.ca/charities-giving", 330f, signatureY - 13f, REGULAR, 7.5f);
    }

    private PDImageXObject loadLogo(PDDocument document) {
        String path = properties.branding().logPath();
        if (path == null || path.isBlank()) return null;
        try {
            ClassPathResource resource = new ClassPathResource(path.replaceFirst("^/", ""));
            if (!resource.exists()) return null;
            return PDImageXObject.createFromByteArray(document, resource.getContentAsByteArray(), "church-logo");
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeCentered(PDPageContentStream stream, String text, float y, PDFont font, float size)
        throws IOException {
        float width = font.getStringWidth(text) / 1000f * size;
        write(stream, text, (PAGE_WIDTH - width) / 2f, y, font, size);
    }

    private void writeFit(
        PDPageContentStream stream,
        String text,
        float x,
        float y,
        float maxWidth,
        PDFont font,
        float preferredSize
    ) throws IOException {
        float size = preferredSize;
        while (size > 5.5f && font.getStringWidth(text) / 1000f * size > maxWidth) size -= 0.25f;
        write(stream, text, x, y, font, size);
    }

    private void writeWrapped(
        PDPageContentStream stream,
        String text,
        float x,
        float y,
        float maxWidth,
        PDFont font,
        float size,
        float leading,
        int maxLines
    ) throws IOException {
        List<String> lines = wrap(text, font, size, maxWidth);
        for (int index = 0; index < Math.min(lines.size(), maxLines); index++) {
            write(stream, lines.get(index), x, y - index * leading, font, size);
        }
    }

    private List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.replace('\n', ' ').split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(candidate) / 1000f * size > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void write(PDPageContentStream stream, String text, float x, float y, PDFont font, float size)
        throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value(text));
        stream.endText();
    }

    private String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.CANADA);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
