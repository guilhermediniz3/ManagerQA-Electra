package com.tester;

import com.tester.entity.DocCard;
import com.tester.service.DocExportService;
import com.itextpdf.text.pdf.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class DocImageExportTest {
    private final DocExportService service = new DocExportService();

    private String data(String format) throws Exception {
        var image = new BufferedImage(240, 120, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.BLUE);
        graphics.fillRect(0, 0, 240, 120);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private DocCard card(String html) {
        var card = new DocCard();
        card.setTitle("Imagens de documentação");
        card.setContent(html);
        return card;
    }

    private void assertImages(String html, int count) throws Exception {
        try (var word = new XWPFDocument(new ByteArrayInputStream(service.word(card(html))))) {
            assertEquals(count, word.getParagraphs().stream().flatMap(p -> p.getRuns().stream())
                    .mapToInt(r -> r.getEmbeddedPictures().size()).sum());
            assertFalse(word.getAllPictures().isEmpty());
        }
        var pdf = new PdfReader(service.pdf(card(html)));
        int images = 0;
        try {
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                var resources = pdf.getPageN(i).getAsDict(PdfName.RESOURCES);
                var objects = resources.getAsDict(PdfName.XOBJECT);
                if (objects != null) for (var key : objects.getKeys()) {
                    var object = (PdfDictionary) PdfReader.getPdfObject(objects.get(key));
                    if (PdfName.IMAGE.equals(object.getAsName(PdfName.SUBTYPE))) images++;
                }
            }
            assertTrue(images >= 1, "PDF must contain an actual image XObject");
        } finally { pdf.close(); }
    }

    @Test void exportsLegacyPlaceholderAndNewHtmlWithoutDuplicatingImage() throws Exception {
        String src = data("png");
        assertImages("<div data-resizable-image data-width='350' data-src='" + src + "'></div>", 1);
        assertImages("<div data-resizable-image data-width='350' data-src='" + src + "'><img src='" + src + "'></div>", 1);
    }

    @Test void exportsInlineJpegAndImageOnlyParagraph() throws Exception {
        assertImages("<p>Antes<img src='" + data("jpg") + "'>Depois</p>", 1);
        assertImages("<p><img src='" + data("png") + "'></p>", 1);
    }

    @Test void corruptImageProducesAnExplicitErrorInsteadOfDisappearing() {
        var card = card("<img src='data:image/png;base64,broken'>");
        assertThrows(IllegalStateException.class, () -> service.word(card));
        assertThrows(IllegalStateException.class, () -> service.pdf(card));
    }

    @Test void webpReaderIsAvailableForPreviouslySavedDocuments() throws Exception {
        assertTrue(ImageIO.getImageReadersByMIMEType("image/webp").hasNext());
        try (var input = getClass().getResourceAsStream("/images/legacy.webp")) {
            assertNotNull(input);
            String src = "data:image/webp;base64," + Base64.getEncoder().encodeToString(input.readAllBytes());
            String html = "<div data-resizable-image data-width='350' data-src='" + src + "'></div>";
            assertImages(html, 1);
            var output = java.nio.file.Path.of("target", "image-validation");
            java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.write(output.resolve("legacy-image.docx"), service.word(card(html)));
            java.nio.file.Files.write(output.resolve("legacy-image.pdf"), service.pdf(card(html)));
        }
    }
}
