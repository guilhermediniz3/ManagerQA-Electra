package com.tester.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import com.tester.entity.DocCard;

/**
 * Um card de documentação em Word e em PDF.
 *
 * O conteúdo do card é o HTML do editor, com nós próprios dele — aviso
 * colorido, selo, vídeo, imagem. Exportar não é despejar esse HTML num arquivo:
 * é percorrer a árvore e desenhar cada elemento no formato de destino, com a
 * mesma cor e o mesmo peso que ele tem na tela. O documento em Word vai para a
 * área de requisitos, e chegar lá como texto cru desmontado seria pior do que
 * não existir.
 *
 * Vídeo é o único que não atravessa: nem Word nem PDF tocam um player. No lugar
 * dele vai o endereço, como link clicável e com a legenda ao lado — que é o que
 * quem lê o requisito precisa para assistir.
 */
@Service
public class DocExportService {

    /** As cores dos avisos, as mesmas do editor. */
    private record Cores(String fundo, String borda, String texto, String rotulo) {}

    private static final java.util.Map<String, Cores> AVISOS = java.util.Map.of(
            "info", new Cores("E0F2FE", "0284C7", "0369A1", "Informação"),
            "success", new Cores("E6F4EA", "1E8E3E", "0D652D", "Tudo certo"),
            "warning", new Cores("FEF7E0", "F9AB00", "8F5D01", "Atenção"),
            "error", new Cores("FCE8E6", "EA4335", "C5221F", "Cuidado"));

    private static final Cores AVISO_PADRAO = new Cores("F1F5F9", "64748B", "334155", "Nota");

    /** Cinza do texto de apoio, usado no cabeçalho e nas legendas. */
    private static final String CINZA = "64748B";

    /** Azul do produto: títulos e links. */
    private static final String AZUL = "1D4ED8";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Word ─────────────────────────────────────────────────────────────────

    public byte[] word(DocCard card) {
        try (XWPFDocument documento = new XWPFDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            margens(documento);
            cabecalhoWord(documento, card);

            Document html = Jsoup.parseBodyFragment(card.getContent() == null ? "" : card.getContent());
            for (Node filho : html.body().childNodes()) blocoWord(documento, filho, 0);

            documento.write(saida);
            return saida.toByteArray();
        } catch (Exception falha) {
            throw new IllegalStateException("Não foi possível gerar o Word: " + falha.getMessage(), falha);
        }
    }

    /** Margens de documento de requisito: folga para anotação na lateral. */
    private void margens(XWPFDocument documento) {
        CTSectPr secao = documento.getDocument().getBody().addNewSectPr();
        CTPageMar margem = secao.addNewPgMar();
        margem.setTop(BigInteger.valueOf(1134));     // 2 cm
        margem.setBottom(BigInteger.valueOf(1134));
        margem.setLeft(BigInteger.valueOf(1418));    // 2,5 cm
        margem.setRight(BigInteger.valueOf(1418));
    }

    private void cabecalhoWord(XWPFDocument documento, DocCard card) {
        XWPFParagraph titulo = documento.createParagraph();
        titulo.setSpacingAfter(60);
        XWPFRun texto = titulo.createRun();
        texto.setText(card.getTitle() == null ? "Documentação" : card.getTitle());
        texto.setBold(true);
        texto.setFontSize(22);
        texto.setColor(AZUL);
        texto.setFontFamily("Calibri");

        XWPFParagraph legenda = documento.createParagraph();
        legenda.setSpacingAfter(120);
        XWPFRun corpo = legenda.createRun();
        StringBuilder linha = new StringBuilder("QA Manager · documentação");
        if (card.getDescription() != null && !card.getDescription().isBlank())
            linha.append("  ·  ").append(card.getDescription().trim());
        linha.append("  ·  ").append(LocalDate.now().format(DATA));
        corpo.setText(linha.toString());
        corpo.setFontSize(9);
        corpo.setColor(CINZA);
        corpo.setFontFamily("Calibri");

        // Uma régua fina separando o cabeçalho do conteúdo.
        XWPFParagraph regua = documento.createParagraph();
        regua.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        regua.setSpacingAfter(200);
    }

    /**
     * Um nó do HTML vira um bloco no Word.
     *
     * O recuo acompanha a profundidade das listas; fora delas é zero. Elemento
     * que não se reconhece não é descartado: entra como parágrafo comum, porque
     * perder texto do requisito é pior do que perder o estilo dele.
     */
    private void blocoWord(XWPFDocument documento, Node no, int nivel) {
        if (no instanceof TextNode texto) {
            if (!texto.text().isBlank()) paragrafoWord(documento, texto.text(), nivel, null);
            return;
        }
        if (!(no instanceof org.jsoup.nodes.Element elemento)) return;

        String tag = elemento.tagName().toLowerCase(Locale.ROOT);

        if (elemento.hasAttr("data-resizable-video") || elemento.hasAttr("data-recording-video")) {
            videoWord(documento, elemento, nivel);
            return;
        }
        if (elemento.hasAttr("data-resizable-image") || tag.equals("img")) {
            imagemWord(documento, elemento, nivel);
            return;
        }
        if (elemento.hasClass("udoc-callout")) {
            avisoWord(documento, elemento, nivel);
            return;
        }

        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> tituloWord(documento, elemento, tag);
            case "ul", "ol" -> {
                int indice = 1;
                for (org.jsoup.nodes.Element item : elemento.children()) {
                    String marcador = tag.equals("ol") ? (indice++) + ". " : "•  ";
                    itemDeListaWord(documento, item, nivel, marcador);
                }
            }
            case "blockquote" -> citacaoWord(documento, elemento, nivel);
            case "pre" -> codigoWord(documento, elemento.text(), nivel);
            case "table" -> tabelaWord(documento, elemento);
            case "hr" -> {
                XWPFParagraph regua = documento.createParagraph();
                regua.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
                regua.setSpacingAfter(160);
            }
            case "br" -> { }
            case "div", "section", "article", "body" -> {
                for (Node filho : elemento.childNodes()) blocoWord(documento, filho, nivel);
            }
            default -> {
                if (elemento.text().isBlank() && elemento.select("img, div[data-src]").isEmpty()) return;
                XWPFParagraph paragrafo = novoParagrafo(documento, nivel);
                escreverFilhos(paragrafo, elemento, new Estilo());
            }
        }
    }

    private XWPFParagraph novoParagrafo(XWPFDocument documento, int nivel) {
        XWPFParagraph paragrafo = documento.createParagraph();
        paragrafo.setSpacingAfter(120);
        paragrafo.setSpacingBetween(1.15);
        if (nivel > 0) paragrafo.setIndentationLeft(360 * nivel);
        return paragrafo;
    }

    private void paragrafoWord(XWPFDocument documento, String texto, int nivel, String cor) {
        XWPFParagraph paragrafo = novoParagrafo(documento, nivel);
        XWPFRun corpo = paragrafo.createRun();
        corpo.setText(texto);
        corpo.setFontSize(11);
        corpo.setFontFamily("Calibri");
        if (cor != null) corpo.setColor(cor);
    }

    private void tituloWord(XWPFDocument documento, org.jsoup.nodes.Element elemento, String tag) {
        int grau = Integer.parseInt(tag.substring(1));
        XWPFParagraph paragrafo = documento.createParagraph();
        paragrafo.setSpacingBefore(grau <= 2 ? 280 : 200);
        paragrafo.setSpacingAfter(100);
        // O h1 do conteúdo ganha uma régua: é o que separa as seções grandes.
        if (grau == 1) paragrafo.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);

        XWPFRun texto = paragrafo.createRun();
        texto.setText(elemento.text());
        texto.setBold(true);
        texto.setFontFamily("Calibri");
        texto.setColor(grau <= 2 ? AZUL : "1E293B");
        texto.setFontSize(switch (grau) {
            case 1 -> 16;
            case 2 -> 14;
            case 3 -> 12;
            default -> 11;
        });
    }

    private void itemDeListaWord(XWPFDocument documento, org.jsoup.nodes.Element item, int nivel, String marcador) {
        // Lista dentro de lista desce um nível e sai como bloco próprio.
        org.jsoup.select.Elements aninhadas = item.children().select("ul, ol");
        XWPFParagraph paragrafo = documento.createParagraph();
        paragrafo.setSpacingAfter(60);
        paragrafo.setIndentationLeft(360 * (nivel + 1));
        paragrafo.setIndentationHanging(220);

        XWPFRun ponto = paragrafo.createRun();
        ponto.setText(marcador);
        ponto.setFontSize(11);
        ponto.setFontFamily("Calibri");
        ponto.setColor(AZUL);

        org.jsoup.nodes.Element copia = item.clone();
        copia.select("ul, ol").remove();
        escreverFilhos(paragrafo, copia, new Estilo());

        for (org.jsoup.nodes.Element aninhada : aninhadas) blocoWord(documento, aninhada, nivel + 1);
    }

    /**
     * Aviso colorido: fundo, barra lateral e rótulo, como na tela.
     *
     * É o elemento que mais se perde numa exportação malfeita — vira parágrafo
     * comum e o documento deixa de distinguir um alerta de um parágrafo
     * qualquer, que é justamente o que ele existe para fazer.
     */
    private void avisoWord(XWPFDocument documento, org.jsoup.nodes.Element elemento, int nivel) {
        Cores cores = AVISOS.getOrDefault(tipoDoAviso(elemento), AVISO_PADRAO);

        XWPFParagraph rotulo = documento.createParagraph();
        rotulo.setSpacingBefore(160);
        rotulo.setSpacingAfter(0);
        rotulo.setIndentationLeft(360 * nivel);
        pintar(rotulo, cores.fundo());
        barraLateral(rotulo, cores.borda());
        XWPFRun nome = rotulo.createRun();
        nome.setText(cores.rotulo().toUpperCase(Locale.ROOT));
        nome.setBold(true);
        nome.setFontSize(8);
        nome.setFontFamily("Calibri");
        nome.setColor(cores.borda());

        String texto = elemento.text().trim();
        XWPFParagraph corpo = documento.createParagraph();
        corpo.setSpacingAfter(160);
        corpo.setIndentationLeft(360 * nivel);
        pintar(corpo, cores.fundo());
        barraLateral(corpo, cores.borda());
        XWPFRun escrita = corpo.createRun();
        escrita.setText(texto.isEmpty() ? " " : texto);
        escrita.setFontSize(11);
        escrita.setFontFamily("Calibri");
        escrita.setColor(cores.texto());
    }

    private String tipoDoAviso(org.jsoup.nodes.Element elemento) {
        for (String classe : elemento.classNames())
            if (classe.startsWith("callout-")) return classe.substring("callout-".length());
        return "info";
    }

    private void pintar(XWPFParagraph paragrafo, String cor) {
        CTPPr propriedades = paragrafo.getCTP().isSetPPr()
                ? paragrafo.getCTP().getPPr() : paragrafo.getCTP().addNewPPr();
        CTShd fundo = propriedades.isSetShd() ? propriedades.getShd() : propriedades.addNewShd();
        fundo.setVal(STShd.CLEAR);
        fundo.setColor("auto");
        fundo.setFill(cor);
    }

    private void barraLateral(XWPFParagraph paragrafo, String cor) {
        CTPPr propriedades = paragrafo.getCTP().isSetPPr()
                ? paragrafo.getCTP().getPPr() : paragrafo.getCTP().addNewPPr();
        CTBorder borda = (propriedades.isSetPBdr() ? propriedades.getPBdr() : propriedades.addNewPBdr()).addNewLeft();
        borda.setVal(STBorder.SINGLE);
        borda.setSz(BigInteger.valueOf(18));
        borda.setColor(cor);
        borda.setSpace(BigInteger.valueOf(8));
    }

    private void citacaoWord(XWPFDocument documento, org.jsoup.nodes.Element elemento, int nivel) {
        XWPFParagraph paragrafo = documento.createParagraph();
        paragrafo.setSpacingAfter(140);
        paragrafo.setIndentationLeft(360 * (nivel + 1));
        barraLateral(paragrafo, "CBD5E1");
        XWPFRun texto = paragrafo.createRun();
        texto.setText(elemento.text());
        texto.setItalic(true);
        texto.setFontSize(11);
        texto.setFontFamily("Calibri");
        texto.setColor("475569");
    }

    private void codigoWord(XWPFDocument documento, String conteudo, int nivel) {
        XWPFParagraph paragrafo = documento.createParagraph();
        paragrafo.setSpacingAfter(140);
        paragrafo.setIndentationLeft(360 * nivel);
        pintar(paragrafo, "F1F5F9");
        for (String linha : conteudo.split("\\R", -1)) {
            XWPFRun texto = paragrafo.createRun();
            texto.setText(linha);
            texto.setFontFamily("Consolas");
            texto.setFontSize(9);
            texto.setColor("0F172A");
            texto.addBreak();
        }
    }

    private void tabelaWord(XWPFDocument documento, org.jsoup.nodes.Element elemento) {
        org.jsoup.select.Elements linhas = elemento.select("tr");
        if (linhas.isEmpty()) return;

        org.apache.poi.xwpf.usermodel.XWPFTable tabela =
                documento.createTable(linhas.size(), Math.max(1, linhas.get(0).select("th, td").size()));
        tabela.setWidth("100%");

        for (int l = 0; l < linhas.size(); l++) {
            org.jsoup.select.Elements celulas = linhas.get(l).select("th, td");
            boolean cabecalho = !linhas.get(l).select("th").isEmpty();
            for (int c = 0; c < celulas.size() && c < tabela.getRow(l).getTableCells().size(); c++) {
                org.apache.poi.xwpf.usermodel.XWPFTableCell celula = tabela.getRow(l).getCell(c);
                celula.removeParagraph(0);
                XWPFParagraph paragrafo = celula.addParagraph();
                paragrafo.setSpacingAfter(0);
                XWPFRun texto = paragrafo.createRun();
                texto.setText(celulas.get(c).text());
                texto.setFontSize(10);
                texto.setFontFamily("Calibri");
                if (cabecalho) {
                    texto.setBold(true);
                    texto.setColor("FFFFFF");
                    celula.setColor("1E293B");
                }
            }
        }
        XWPFParagraph respiro = documento.createParagraph();
        respiro.setSpacingAfter(160);
    }

    /**
     * O vídeo vira endereço clicável, com a legenda acima.
     *
     * Nenhum dos dois formatos toca vídeo, e foi pedido que o link fosse junto:
     * quem lê o requisito precisa conseguir abrir a gravação.
     */
    private void videoWord(XWPFDocument documento, org.jsoup.nodes.Element elemento, int nivel) {
        String endereco = endereco(elemento);
        if (endereco == null) return;

        XWPFParagraph legenda = documento.createParagraph();
        legenda.setSpacingBefore(120);
        legenda.setSpacingAfter(0);
        legenda.setIndentationLeft(360 * nivel);
        XWPFRun titulo = legenda.createRun();
        titulo.setText("▶  Gravação em vídeo");
        titulo.setBold(true);
        titulo.setFontSize(10);
        titulo.setFontFamily("Calibri");
        titulo.setColor(AZUL);

        XWPFParagraph link = documento.createParagraph();
        link.setSpacingAfter(160);
        link.setIndentationLeft(360 * nivel);
        hiperlink(link, endereco, endereco);
    }

    private void imagemWord(XWPFDocument documento, org.jsoup.nodes.Element elemento, int nivel) {
        XWPFParagraph imagem = novoParagrafo(documento, nivel);
        imagem.setAlignment(switch (elemento.attr("data-align")) {
            case "left" -> ParagraphAlignment.LEFT;
            case "right" -> ParagraphAlignment.RIGHT;
            default -> ParagraphAlignment.CENTER;
        });
        if (inserirImagemWord(imagem, elemento)) return;
        String endereco = endereco(elemento);
        XWPFParagraph paragrafo = novoParagrafo(documento, nivel);
        XWPFRun texto = paragrafo.createRun();
        texto.setText("🖼  Imagem");
        texto.setBold(true);
        texto.setFontSize(10);
        texto.setColor(CINZA);
        texto.setFontFamily("Calibri");
        if (endereco != null && !endereco.startsWith("data:")) {
            XWPFParagraph linha = novoParagrafo(documento, nivel);
            hiperlink(linha, endereco, endereco);
        }
    }

    private boolean inserirImagemWord(XWPFParagraph paragrafo, org.jsoup.nodes.Element elemento) {
        var imagem = DocumentImage.read(elemento);
        if (imagem == null) return false;
        try {
            paragrafo.createRun().addPicture(new java.io.ByteArrayInputStream(imagem.png()),
                    XWPFDocument.PICTURE_TYPE_PNG, "imagem.png",
                    org.apache.poi.util.Units.toEMU(imagem.width()), org.apache.poi.util.Units.toEMU(imagem.height()));
            return true;
        } catch (Exception error) { throw new IllegalStateException("Não foi possível incorporar a imagem no Word.", error); }
    }

    private com.itextpdf.text.Image imagemPdf(org.jsoup.nodes.Element elemento) {
        var imagem = DocumentImage.read(elemento);
        if (imagem == null) return null;
        try {
            var resultado = com.itextpdf.text.Image.getInstance(imagem.png());
            resultado.scaleAbsolute(imagem.width(), imagem.height());
            resultado.setAlignment(switch (elemento.attr("data-align")) {
                case "left" -> com.itextpdf.text.Element.ALIGN_LEFT;
                case "right" -> com.itextpdf.text.Element.ALIGN_RIGHT;
                default -> com.itextpdf.text.Element.ALIGN_CENTER;
            });
            return resultado;
        } catch (Exception error) { throw new IllegalStateException("Não foi possível incorporar a imagem no PDF.", error); }
    }

    private String endereco(org.jsoup.nodes.Element elemento) {
        for (String atributo : List.of("data-src", "src", "href")) {
            String valor = elemento.attr(atributo);
            if (valor != null && !valor.isBlank()) return valor.trim();
            org.jsoup.nodes.Element dentro = elemento.selectFirst("[" + atributo + "]");
            if (dentro != null && !dentro.attr(atributo).isBlank()) return dentro.attr(atributo).trim();
        }
        return null;
    }

    /**
     * Link de verdade, que o Word abre com um clique.
     *
     * O caminho é o {@code createHyperlinkRun} do POI, e não escrever o nó de
     * hiperlink à mão: o relacionamento que aponta para o endereço vive noutra
     * parte do arquivo, e o POI a reescreve ao salvar. Feito à mão, o texto
     * saía azul e sublinhado — parecendo link — apontando para um destino que
     * não existia no pacote.
     */
    private void hiperlink(XWPFParagraph paragrafo, String endereco, String rotulo) {
        org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun corrida =
                paragrafo.createHyperlinkRun(endereco);
        corrida.setText(rotulo);
        corrida.setColor(AZUL);
        corrida.setUnderline(UnderlinePatterns.SINGLE);
        corrida.setFontSize(10);
        corrida.setFontFamily("Calibri");
    }

    /** Negrito, itálico, sublinhado e link acumulados na descida da árvore. */
    private static class Estilo {
        boolean negrito, italico, sublinhado, codigo;
        String cor;
        String link;

        Estilo copiar() {
            Estilo novo = new Estilo();
            novo.negrito = negrito;
            novo.italico = italico;
            novo.sublinhado = sublinhado;
            novo.codigo = codigo;
            novo.cor = cor;
            novo.link = link;
            return novo;
        }
    }

    private void escreverFilhos(XWPFParagraph paragrafo, Node no, Estilo estilo) {
        for (Node filho : no.childNodes()) {
            if (filho instanceof TextNode texto) {
                if (texto.text().isEmpty()) continue;
                if (estilo.link != null) {
                    hiperlink(paragrafo, estilo.link, texto.text());
                    // Um link de gravação escrito como "Ver gravação" some no
                    // papel: quem imprime o requisito fica sem o endereço. Ele
                    // vai junto, miúdo, quando o texto do link não for ele mesmo.
                    if (ehGravacao(estilo.link) && !texto.text().contains("://")) {
                        XWPFRun endereco = paragrafo.createRun();
                        endereco.setText("  " + estilo.link);
                        endereco.setFontSize(8);
                        endereco.setFontFamily("Calibri");
                        endereco.setColor(CINZA);
                    }
                    continue;
                }
                XWPFRun corrida = paragrafo.createRun();
                corrida.setText(texto.text());
                corrida.setBold(estilo.negrito);
                corrida.setItalic(estilo.italico);
                if (estilo.sublinhado) corrida.setUnderline(UnderlinePatterns.SINGLE);
                corrida.setFontSize(estilo.codigo ? 10 : 11);
                corrida.setFontFamily(estilo.codigo ? "Consolas" : "Calibri");
                if (estilo.cor != null) corrida.setColor(estilo.cor);
                continue;
            }
            if (!(filho instanceof org.jsoup.nodes.Element elemento)) continue;

            // Selo colorido: vira um pedaço com a cor dele, entre colchetes.
            if (elemento.hasAttr("data-resizable-image") || elemento.tagName().equals("img")) {
                inserirImagemWord(paragrafo, elemento);
                continue;
            }
            if (elemento.hasAttr("data-badge")) {
                XWPFRun selo = paragrafo.createRun();
                String rotulo = elemento.hasAttr("data-text") ? elemento.attr("data-text") : elemento.text();
                selo.setText(" [" + rotulo + "] ");
                selo.setBold(true);
                selo.setFontSize(9);
                selo.setFontFamily("Calibri");
                selo.setColor(corDoSelo(elemento.attr("data-color")));
                continue;
            }

            Estilo proximo = estilo.copiar();
            switch (elemento.tagName().toLowerCase(Locale.ROOT)) {
                case "strong", "b" -> proximo.negrito = true;
                case "em", "i" -> proximo.italico = true;
                case "u" -> proximo.sublinhado = true;
                case "code" -> proximo.codigo = true;
                case "a" -> proximo.link = elemento.absUrl("href").isBlank()
                        ? elemento.attr("href") : elemento.absUrl("href");
                case "br" -> {
                    paragrafo.createRun().addBreak();
                    continue;
                }
                default -> { }
            }
            escreverFilhos(paragrafo, elemento, proximo);
        }
    }

    /** Endereço de gravação: é o que precisa aparecer escrito no documento. */
    private boolean ehGravacao(String endereco) {
        if (endereco == null) return false;
        String minusculo = endereco.toLowerCase(Locale.ROOT);
        return minusculo.contains("drive.google.com") || minusculo.contains("vimeo.com") || minusculo.contains("youtube.com")
                || minusculo.contains("youtu.be") || minusculo.contains("loom.com");
    }

    /** A cor do selo como o editor a guarda; nome conhecido vira hexadecimal. */
    private String corDoSelo(String cor) {
        if (cor == null || cor.isBlank()) return AZUL;
        String limpa = cor.trim();
        if (limpa.startsWith("#")) return limpa.substring(1).toUpperCase(Locale.ROOT);
        return switch (limpa.toLowerCase(Locale.ROOT)) {
            case "red", "vermelho" -> "DC2626";
            case "green", "verde" -> "16A34A";
            case "yellow", "amarelo" -> "CA8A04";
            case "gray", "grey", "cinza" -> "475569";
            case "purple", "roxo" -> "7C3AED";
            case "orange", "laranja" -> "EA580C";
            default -> AZUL;
        };
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    public byte[] pdf(DocCard card) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            com.itextpdf.text.Document documento =
                    new com.itextpdf.text.Document(PageSize.A4, 56, 56, 48, 56);
            PdfWriter.getInstance(documento, saida);
            documento.open();

            documento.add(tituloPdf(card));
            documento.add(legendaPdf(card));
            documento.add(regua());

            Document html = Jsoup.parseBodyFragment(card.getContent() == null ? "" : card.getContent());
            for (Node filho : html.body().childNodes()) blocoPdf(documento, filho, 0);

            documento.close();
            return saida.toByteArray();
        } catch (Exception falha) {
            throw new IllegalStateException("Não foi possível gerar o PDF: " + falha.getMessage(), falha);
        }
    }

    /**
     * A cor no PDF, componente a componente.
     *
     * O construtor que recebe um número só o lê como ARGB, e um hexadecimal de
     * seis dígitos entra ali com o canal de opacidade zerado: o documento sai
     * com todo o conteúdo desenhado de forma invisível — texto presente,
     * selecionável, e página em branco na tela.
     */
    private static BaseColor cor(String hexadecimal) {
        int valor = Integer.parseInt(hexadecimal, 16);
        return new BaseColor((valor >> 16) & 0xFF, (valor >> 8) & 0xFF, valor & 0xFF);
    }

    /**
     * A fonte do PDF.
     *
     * Helvetica é uma das catorze fontes que todo leitor de PDF já tem, e por
     * isso não se embute: pedir para embuti-la produz um arquivo cujo texto
     * está lá, mas que abre com a página em branco. A codificação Cp1252 cobre
     * a acentuação do português.
     */
    private static Font fonte(float tamanho, int estilo, String cor) {
        return FontFactory.getFont(FontFactory.HELVETICA, com.itextpdf.text.pdf.BaseFont.CP1252,
                com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED, tamanho, estilo, cor(cor));
    }

    private Paragraph tituloPdf(DocCard card) {
        Paragraph titulo = new Paragraph(card.getTitle() == null ? "Documentação" : card.getTitle(),
                fonte(20, Font.BOLD, AZUL));
        titulo.setSpacingAfter(2);
        return titulo;
    }

    private Paragraph legendaPdf(DocCard card) {
        StringBuilder linha = new StringBuilder("QA Manager · documentação");
        if (card.getDescription() != null && !card.getDescription().isBlank())
            linha.append("  ·  ").append(card.getDescription().trim());
        linha.append("  ·  ").append(LocalDate.now().format(DATA));
        Paragraph legenda = new Paragraph(linha.toString(), fonte(8.5f, Font.NORMAL, CINZA));
        legenda.setSpacingAfter(8);
        return legenda;
    }

    private PdfPTable regua() {
        PdfPTable linha = new PdfPTable(1);
        linha.setWidthPercentage(100);
        PdfPCell celula = new PdfPCell();
        celula.setFixedHeight(1f);
        celula.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        celula.setBackgroundColor(cor("E2E8F0"));
        linha.addCell(celula);
        linha.setSpacingAfter(14);
        return linha;
    }

    private void blocoPdf(com.itextpdf.text.Document documento, Node no, int nivel) throws Exception {
        if (no instanceof TextNode texto) {
            if (!texto.text().isBlank()) documento.add(paragrafoPdf(texto.text(), nivel));
            return;
        }
        if (!(no instanceof org.jsoup.nodes.Element elemento)) return;

        String tag = elemento.tagName().toLowerCase(Locale.ROOT);

        if (elemento.hasAttr("data-resizable-video") || elemento.hasAttr("data-recording-video")) {
            videoPdf(documento, elemento, nivel);
            return;
        }
        if (elemento.hasAttr("data-resizable-image") || tag.equals("img")) {
            var imagem = imagemPdf(elemento);
            if (imagem != null) {
                imagem.setSpacingAfter(8);
                documento.add(imagem);
                return;
            }
            String endereco = endereco(elemento);
            if (endereco != null && !endereco.startsWith("data:")) {
                Paragraph linha = new Paragraph("Imagem: ", fonte(9, Font.BOLD, CINZA));
                linha.add(new Chunk(endereco, fonte(9, Font.UNDERLINE, AZUL))
                        .setAnchor(endereco));
                linha.setIndentationLeft(18 * nivel);
                linha.setSpacingAfter(8);
                documento.add(linha);
            }
            return;
        }
        if (elemento.hasClass("udoc-callout")) {
            documento.add(avisoPdf(elemento));
            return;
        }

        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> documento.add(tituloPdf(elemento, tag));
            case "ul", "ol" -> {
                int indice = 1;
                for (org.jsoup.nodes.Element item : elemento.children()) {
                    String marcador = tag.equals("ol") ? (indice++) + ".  " : "•  ";
                    Paragraph linha = new Paragraph();
                    linha.add(new Chunk(marcador, fonte(11, Font.NORMAL, AZUL)));
                    escreverFilhosPdf(linha, item.clone().select("ul, ol").remove().isEmpty()
                            ? item : item.clone(), new Estilo());
                    linha.setIndentationLeft(18 * (nivel + 1));
                    linha.setSpacingAfter(3);
                    documento.add(linha);
                    for (org.jsoup.nodes.Element aninhada : item.children().select("ul, ol"))
                        blocoPdf(documento, aninhada, nivel + 1);
                }
            }
            case "blockquote" -> {
                Paragraph citacao = new Paragraph(elemento.text(), fonte(11, Font.ITALIC, "475569"));
                citacao.setIndentationLeft(18 * (nivel + 1));
                citacao.setSpacingAfter(8);
                documento.add(citacao);
            }
            case "pre" -> {
                PdfPTable bloco = new PdfPTable(1);
                bloco.setWidthPercentage(100);
                PdfPCell celula = new PdfPCell(new Phrase(elemento.text(),
                        FontFactory.getFont(FontFactory.COURIER, com.itextpdf.text.pdf.BaseFont.CP1252,
                                com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED, 9, Font.NORMAL, cor("0F172A"))));
                celula.setBackgroundColor(cor("F1F5F9"));
                celula.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
                celula.setPadding(8);
                bloco.addCell(celula);
                bloco.setSpacingAfter(10);
                documento.add(bloco);
            }
            case "table" -> documento.add(tabelaPdf(elemento));
            case "hr" -> documento.add(regua());
            case "br" -> { }
            case "div", "section", "article", "body" -> {
                for (Node filho : elemento.childNodes()) blocoPdf(documento, filho, nivel);
            }
            default -> {
                if (elemento.text().isBlank() && elemento.select("img, [data-resizable-image]").isEmpty()) return;
                Paragraph paragrafo = new Paragraph();
                escreverFilhosPdf(paragrafo, elemento, new Estilo());
                paragrafo.setIndentationLeft(18 * nivel);
                paragrafo.setSpacingAfter(7);
                paragrafo.setLeading(0, 1.2f);
                documento.add(paragrafo);
            }
        }
    }

    private Paragraph paragrafoPdf(String texto, int nivel) {
        Paragraph paragrafo = new Paragraph(texto, fonte(11, Font.NORMAL, "1E293B"));
        paragrafo.setIndentationLeft(18 * nivel);
        paragrafo.setSpacingAfter(7);
        return paragrafo;
    }

    private Paragraph tituloPdf(org.jsoup.nodes.Element elemento, String tag) {
        int grau = Integer.parseInt(tag.substring(1));
        float tamanho = switch (grau) {
            case 1 -> 15;
            case 2 -> 13;
            case 3 -> 11.5f;
            default -> 11;
        };
        Paragraph titulo = new Paragraph(elemento.text(),
                fonte(tamanho, Font.BOLD, grau <= 2 ? AZUL : "1E293B"));
        titulo.setSpacingBefore(grau <= 2 ? 12 : 9);
        titulo.setSpacingAfter(5);
        return titulo;
    }

    /** O aviso no PDF: uma tabela de uma célula, com fundo e barra na cor. */
    private PdfPTable avisoPdf(org.jsoup.nodes.Element elemento) {
        Cores cores = AVISOS.getOrDefault(tipoDoAviso(elemento), AVISO_PADRAO);
        PdfPTable caixa = new PdfPTable(1);
        caixa.setWidthPercentage(100);

        Paragraph conteudo = new Paragraph();
        conteudo.add(new Chunk(cores.rotulo().toUpperCase(Locale.ROOT) + "\n",
                fonte(8, Font.BOLD, cores.borda())));
        conteudo.add(new Chunk(elemento.text(), fonte(10.5f, Font.NORMAL, cores.texto())));

        PdfPCell celula = new PdfPCell(conteudo);
        celula.setBackgroundColor(cor(cores.fundo()));
        celula.setBorder(com.itextpdf.text.Rectangle.LEFT);
        celula.setBorderColor(cor(cores.borda()));
        celula.setBorderWidthLeft(3f);
        celula.setPadding(9);
        caixa.addCell(celula);
        caixa.setSpacingBefore(6);
        caixa.setSpacingAfter(10);
        return caixa;
    }

    private PdfPTable tabelaPdf(org.jsoup.nodes.Element elemento) {
        org.jsoup.select.Elements linhas = elemento.select("tr");
        int colunas = Math.max(1, linhas.isEmpty() ? 1 : linhas.get(0).select("th, td").size());
        PdfPTable tabela = new PdfPTable(colunas);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(6);
        tabela.setSpacingAfter(10);

        for (org.jsoup.nodes.Element linha : linhas) {
            boolean cabecalho = !linha.select("th").isEmpty();
            for (org.jsoup.nodes.Element celula : linha.select("th, td")) {
                PdfPCell caixa = new PdfPCell(new Phrase(celula.text(),
                        fonte(9.5f, cabecalho ? Font.BOLD : Font.NORMAL, cabecalho ? "FFFFFF" : "1E293B")));
                if (cabecalho) caixa.setBackgroundColor(cor("1E293B"));
                caixa.setBorderColor(cor("E2E8F0"));
                caixa.setPadding(6);
                tabela.addCell(caixa);
            }
        }
        return tabela;
    }

    private void videoPdf(com.itextpdf.text.Document documento, org.jsoup.nodes.Element elemento, int nivel)
            throws Exception {
        String endereco = endereco(elemento);
        if (endereco == null) return;
        Paragraph titulo = new Paragraph("Gravação em vídeo", fonte(10, Font.BOLD, AZUL));
        titulo.setIndentationLeft(18 * nivel);
        titulo.setSpacingBefore(8);
        titulo.setSpacingAfter(1);
        documento.add(titulo);

        Paragraph link = new Paragraph();
        link.add(new Chunk(endereco, fonte(9.5f, Font.UNDERLINE, AZUL)).setAnchor(endereco));
        link.setIndentationLeft(18 * nivel);
        link.setSpacingAfter(10);
        documento.add(link);
    }

    private void escreverFilhosPdf(Paragraph paragrafo, Node no, Estilo estilo) {
        for (Node filho : no.childNodes()) {
            if (filho instanceof TextNode texto) {
                if (texto.text().isEmpty()) continue;
                int decoracao = (estilo.negrito ? Font.BOLD : 0) | (estilo.italico ? Font.ITALIC : 0)
                        | (estilo.sublinhado ? Font.UNDERLINE : 0);
                Chunk pedaco = estilo.codigo
                        ? new Chunk(texto.text(), FontFactory.getFont(FontFactory.COURIER, com.itextpdf.text.pdf.BaseFont.CP1252,
                                com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED, 9.5f, Font.NORMAL, cor("0F172A")))
                        : new Chunk(texto.text(), fonte(11, decoracao, estilo.cor != null ? estilo.cor : "1E293B"));
                if (estilo.link != null) {
                    pedaco.setAnchor(estilo.link);
                    pedaco.setFont(fonte(11, decoracao | Font.UNDERLINE, AZUL));
                }
                paragrafo.add(pedaco);
                if (estilo.link != null && ehGravacao(estilo.link) && !texto.text().contains("://"))
                    paragrafo.add(new Chunk("  " + estilo.link, fonte(8, Font.NORMAL, CINZA)));
                continue;
            }
            if (!(filho instanceof org.jsoup.nodes.Element elemento)) continue;

            if (elemento.hasAttr("data-resizable-image") || elemento.tagName().equals("img")) {
                var imagem = imagemPdf(elemento);
                if (imagem != null) paragrafo.add(new Chunk(imagem, 0, 0, true));
                continue;
            }

            if (elemento.hasAttr("data-badge")) {
                String rotulo = elemento.hasAttr("data-text") ? elemento.attr("data-text") : elemento.text();
                paragrafo.add(new Chunk(" [" + rotulo + "] ",
                        fonte(9, Font.BOLD, corDoSelo(elemento.attr("data-color")))));
                continue;
            }

            Estilo proximo = estilo.copiar();
            switch (elemento.tagName().toLowerCase(Locale.ROOT)) {
                case "strong", "b" -> proximo.negrito = true;
                case "em", "i" -> proximo.italico = true;
                case "u" -> proximo.sublinhado = true;
                case "code" -> proximo.codigo = true;
                case "a" -> proximo.link = elemento.attr("href");
                case "br" -> {
                    paragrafo.add(Chunk.NEWLINE);
                    continue;
                }
                default -> { }
            }
            escreverFilhosPdf(paragrafo, elemento, proximo);
        }
    }

    /** O nome do arquivo: título do card, sem o que o Windows recusa. */
    public String nomeDoArquivo(DocCard card, String extensao) {
        String base = card.getTitle() == null || card.getTitle().isBlank() ? "documentacao" : card.getTitle();
        String limpo = base.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ");
        if (limpo.length() > 80) limpo = limpo.substring(0, 80).trim();
        return limpo + " - " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "." + extensao;
    }
}
