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
    private static final float FONT_SIZE_INCREASE = 2f;
    static final float LOGO_MAX_WIDTH = 112.32f;
    static final float LOGO_MAX_HEIGHT = 68.64f;
    private static final float LOGO_TEXT_GAP = 10f;
    private static final float HEADER_TOP_OFFSET = 34f;
    private static final float HEADER_BOTTOM_OFFSET = 90f;
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
        writeCentered(stream, "Official Receipt for Income Tax Purposes", titleY, BOLD, fontSize(12f));

        float churchX = LEFT;
        if (logo != null) {
            float scale = Math.min(LOGO_MAX_WIDTH / logo.getWidth(), LOGO_MAX_HEIGHT / logo.getHeight());
            float renderedWidth = logo.getWidth() * scale;
            float renderedHeight = logo.getHeight() * scale;
            float headerCenterY = originY - ((HEADER_TOP_OFFSET + HEADER_BOTTOM_OFFSET) / 2f);
            float logoY = headerCenterY - (renderedHeight / 2f);
            stream.drawImage(logo, LEFT, logoY, renderedWidth, renderedHeight);
            churchX = LEFT + renderedWidth + LOGO_TEXT_GAP;
        }
        write(stream, receipt.getChurchName(), churchX, originY - 43f, BOLD, fontSize(10f));
        writeFit(stream, receipt.getChurchAddress(), churchX, originY - 56f, PAGE_WIDTH - churchX - 30f, REGULAR, fontSize(7.5f));
        write(stream, "Charity registration: " + value(receipt.getCharityRegistrationNumber()), churchX, originY - 68f, REGULAR, fontSize(7.5f));
        writeFit(stream, value(receipt.getChurchWebsite()), churchX, originY - 80f, PAGE_WIDTH - churchX - 30f, REGULAR, fontSize(7.5f));

        write(stream, "Receipt number: " + value(receipt.getReceiptNumber()), LEFT, originY - 101f, BOLD, fontSize(8.5f));
        write(stream, "Receipt issued: " + value(receipt.getIssueDate()), LEFT, originY - 114f, REGULAR, fontSize(8f));
        write(stream, "Issued at: " + value(receipt.getReceiptIssueLocation()), LEFT, originY - 127f, REGULAR, fontSize(8f));
        write(stream, "Donations received during: " + receipt.getTaxYear(), 330f, originY - 101f, REGULAR, fontSize(8f));

        write(stream, "Donor: " + value(receipt.getDonorName()), LEFT, originY - 150f, BOLD, fontSize(9.5f));
        writeFit(stream, value(receipt.getDonorAddress()), LEFT, originY - 165f, CONTENT_WIDTH, REGULAR, fontSize(8.5f));
        write(stream, "Amount: " + money(receipt.getGiftAmount()), LEFT, originY - 190f, BOLD, fontSize(11f));

        write(stream, "Thank you", LEFT, originY - 214f, BOLD, fontSize(8.5f));
        writeWrapped(stream, value(receipt.getThankYouNote()), LEFT, originY - 228f, CONTENT_WIDTH, REGULAR, fontSize(7.5f), 12f, 3);

        float signatureY = originY - 290f;
        stream.setStrokingColor(45f / 255f);
        stream.moveTo(LEFT, signatureY);
        stream.lineTo(220f, signatureY);
        stream.stroke();
        write(stream, signerTitle(receipt.getTreasurerName()), LEFT, signatureY - 12f, REGULAR, fontSize(7.5f));
        write(stream, "Authorized signature", LEFT, signatureY + 4f, REGULAR, fontSize(6.5f));
        write(stream, "Canada Revenue Agency", 330f, signatureY - 1f, BOLD, fontSize(7.5f));
        write(stream, "canada.ca/charities-giving", 330f, signatureY - 13f, REGULAR, fontSize(7.5f));
    }

    private PDImageXObject loadLogo(PDDocument document) {
        String path = properties.branding().logPath();
        if (path == null || path.isBlank()) return null;
        String normalized = path.replaceFirst("^/", "");
        for (String candidate : List.of(normalized, "static/" + normalized)) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    return PDImageXObject.createFromByteArray(document, resource.getContentAsByteArray(), "church-logo");
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Try the next classpath form before falling back to text-only output.
            }
        }
        return null;
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

    private float fontSize(float currentSize) {
        return currentSize + FONT_SIZE_INCREASE;
    }

    private String signerTitle(String treasurerName) {
        if (treasurerName == null || treasurerName.isBlank()) return "Treasurer";
        return treasurerName.equalsIgnoreCase("Treasurer") ? "Treasurer" : treasurerName + ", Treasurer";
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
