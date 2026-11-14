package com.tester.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.jsoup.nodes.Element;

/** Decodes embedded editor images without fetching URLs or local files. */
final class DocumentImage {
    record Picture(byte[] png, float width, float height) {}

    static Picture read(Element element) {
        String source = element.hasAttr("data-src") ? element.attr("data-src") : element.attr("src");
        if (!source.startsWith("data:")) return null;
        int comma = source.indexOf(',');
        if (comma < 0 || !source.substring(0, comma).matches("(?i)data:image/(png|jpe?g|gif|webp);base64"))
            throw new IllegalArgumentException("Formato de imagem incorporada não suportado.");
        if (source.length() > 28_000_000)
            throw new IllegalArgumentException("Imagem muito grande para exportar.");
        try {
            byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1).replaceAll("\\s", ""));
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Imagem não reconhecida.");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 25_000_000)
                        throw new IllegalArgumentException("Dimensões de imagem inválidas ou muito grandes.");
                    var image = reader.read(0);
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("Codificador PNG indisponível.");
                    float requested = width;
                    try {
                        requested = Float.parseFloat(element.hasAttr("data-width") ? element.attr("data-width") : element.attr("width"));
                    } catch (NumberFormatException ignored) { }
                    if (!Float.isFinite(requested) || requested <= 0) requested = width;
                    float displayWidth = Math.min(requested * 0.75f, 460);
                    float displayHeight = displayWidth * height / width;
                    if (displayHeight > 650) { displayWidth *= 650 / displayHeight; displayHeight = 650; }
                    return new Picture(output.toByteArray(), displayWidth, displayHeight);
                } finally { reader.dispose(); }
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("Não foi possível ler uma imagem do documento. Insira novamente a imagem.", error);
        }
    }

    private DocumentImage() {}
}
