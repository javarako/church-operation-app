package com.church.operation.service;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.entity.TaxReceipt;
import com.church.operation.util.TaxReceiptStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TaxReceiptPdfServiceTest {
    private static final String NOTE = "Thank you for your faithful and generous support over the past year. Because of you, we are able to continue serving our community and sharing God's message.";

    @Test
    void usesTwentyPercentLargerLogoBounds() {
        assertThat(TaxReceiptPdfService.LOGO_MAX_WIDTH).isEqualTo(112.32f);
        assertThat(TaxReceiptPdfService.LOGO_MAX_HEIGHT).isEqualTo(68.64f);
    }

    @Test
    void rendersTwoIdenticalOfficialReceiptsOnOneLetterPage() throws Exception {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Grace Community Church", "123 Church Street, Toronto, ON M1A 1A1", "416-555-0100",
                "Daniel Kim", "123456789RR0001", "Toronto, Ontario", "https://grace.example.org"
            ),
            new ChurchInformationProperties.Branding("/banner.png", "/missing-logo.png"),
            new ChurchInformationProperties.Ui(20)
        );
        TaxReceiptPdfService service = new TaxReceiptPdfService(properties);

        byte[] pdf = service.render(receipt());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(612f);
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(792f);
            String text = new PDFTextStripper().getText(document);
            assertOccursTwice(text, "Official Receipt for Income Tax Purposes");
            assertOccursTwice(text, "2026-000001");
            assertOccursTwice(text, "Grace Community Church");
            assertOccursTwice(text, "123 Church Street, Toronto, ON M1A 1A1");
            assertOccursTwice(text, "123456789RR0001");
            assertOccursTwice(text, "Toronto, Ontario");
            assertOccursTwice(text, "Donations received during: 2026");
            assertOccursTwice(text, "Ada Wong");
            assertOccursTwice(text, "100 Main St, Toronto, ON, M1M 1M1, Canada");
            assertOccursTwice(text, "Amount: $1,245.50");
            assertThat(text).doesNotContain(
                "Offering number:",
                "Amount of gift:",
                "Advantage amount:",
                "Advantage description:",
                "Eligible amount:"
            );
            assertOccursTwice(text, "Daniel Kim, Treasurer");
            assertOccursTwice(text, "https://grace.example.org");
            assertOccursTwice(text, "Canada Revenue Agency");
            assertOccursTwice(text, "canada.ca/charities-giving");
            assertOccursTwice(text.replaceAll("\\s+", " "), NOTE);
        }
    }

    @Test
    void embedsConfiguredClasspathLogo() throws Exception {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Grace Community Church", "123 Church Street", "416-555-0100", "Daniel Kim",
                "123456789RR0001", "Toronto, Ontario", "https://grace.example.org"
            ),
            new ChurchInformationProperties.Branding("/branding/church-banner.png", "/branding/church_logo.png"),
            new ChurchInformationProperties.Ui(20)
        );

        TaxReceipt receipt = receipt();
        receipt.setTreasurerName("Treasurer");
        try (PDDocument document = Loader.loadPDF(new TaxReceiptPdfService(properties).render(receipt))) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("Treasurer, Treasurer");
            List<float[]> imageTransforms = imageTransforms(document);
            assertThat(imageTransforms).hasSize(2);
            assertThat(imageTransforms).allSatisfy(transform -> {
                assertThat(transform[0]).isCloseTo(112.32f, offset(0.01f));
                assertThat(transform[1]).isCloseTo(38.11f, offset(0.01f));
                assertThat(transform[2]).isEqualTo(30f);
            });
            assertThat(imageTransforms.get(0)[3]).isCloseTo(710.95f, offset(0.01f));
            assertThat(imageTransforms.get(1)[3]).isCloseTo(314.95f, offset(0.01f));
            assertThat(textXPositions(document, "Grace Community Church")).allSatisfy(
                x -> assertThat(x).isCloseTo(152.32f, offset(0.01f))
            );
        }
    }

    @Test
    void usesTwoPointLargerTypographyThroughoutTheReceipt() throws Exception {
        ChurchInformationProperties properties = new ChurchInformationProperties(
            new ChurchInformationProperties.Information(
                "Grace Community Church", "123 Church Street", "416-555-0100", "Daniel Kim",
                "123456789RR0001", "Toronto, Ontario", "https://grace.example.org"
            ),
            new ChurchInformationProperties.Branding("/banner.png", "/missing-logo.png"),
            new ChurchInformationProperties.Ui(20)
        );

        try (PDDocument document = Loader.loadPDF(new TaxReceiptPdfService(properties).render(receipt()))) {
            List<Float> fontSizes = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    fontSizes.add(text.getFontSizeInPt());
                    super.processTextPosition(text);
                }
            };
            stripper.getText(document);

            assertThat(fontSizes).isNotEmpty();
            assertThat(fontSizes).allMatch(size -> size >= 8f);
            assertThat(fontSizes).anyMatch(size -> size == 14f);
        }
    }

    private TaxReceipt receipt() {
        TaxReceipt receipt = new TaxReceipt();
        receipt.setReceiptNumber("2026-000001");
        receipt.setStatus(TaxReceiptStatus.ISSUED);
        receipt.setTaxYear(2026);
        receipt.setIssueDate(LocalDate.of(2027, 2, 15));
        receipt.setOfferingNumber("1001");
        receipt.setDonorName("Ada Wong");
        receipt.setDonorAddress("100 Main St, Toronto, ON, M1M 1M1, Canada");
        receipt.setDonorEmail("ada@example.org");
        receipt.setChurchName("Grace Community Church");
        receipt.setChurchAddress("123 Church Street, Toronto, ON M1A 1A1");
        receipt.setCharityRegistrationNumber("123456789RR0001");
        receipt.setChurchWebsite("https://grace.example.org");
        receipt.setReceiptIssueLocation("Toronto, Ontario");
        receipt.setTreasurerName("Daniel Kim");
        receipt.setGiftAmount(new BigDecimal("1245.50"));
        receipt.setEligibleAmount(new BigDecimal("1245.50"));
        receipt.setAdvantageAmount(new BigDecimal("0.00"));
        receipt.setAdvantageDescription("None");
        receipt.setThankYouNote(NOTE);
        receipt.setSourceOfferingIds(List.of("o1", "o2"));
        return receipt;
    }

    private void assertOccursTwice(String text, String value) {
        assertThat(text.split(java.util.regex.Pattern.quote(value), -1)).hasSize(3);
    }

    private List<float[]> imageTransforms(PDDocument document) throws Exception {
        List<Object> tokens = new PDFStreamParser(document.getPage(0)).parse();
        List<float[]> transforms = new ArrayList<>();
        for (int index = 6; index < tokens.size(); index++) {
            if (tokens.get(index) instanceof Operator operator && "cm".equals(operator.getName())) {
                transforms.add(new float[]{
                    ((COSNumber) tokens.get(index - 6)).floatValue(),
                    ((COSNumber) tokens.get(index - 3)).floatValue(),
                    ((COSNumber) tokens.get(index - 2)).floatValue(),
                    ((COSNumber) tokens.get(index - 1)).floatValue(),
                });
            }
        }
        return transforms;
    }

    private List<Float> textXPositions(PDDocument document, String text) throws Exception {
        List<Object> tokens = new PDFStreamParser(document.getPage(0)).parse();
        List<Float> positions = new ArrayList<>();
        float currentX = 0f;
        for (int index = 2; index < tokens.size(); index++) {
            if (!(tokens.get(index) instanceof Operator operator)) {
                continue;
            }
            if ("Td".equals(operator.getName())) {
                currentX = ((COSNumber) tokens.get(index - 2)).floatValue();
            } else if ("Tj".equals(operator.getName())
                && tokens.get(index - 1) instanceof COSString value
                && text.equals(value.getString())) {
                positions.add(currentX);
            }
        }
        return positions;
    }
}
