package com.tester.demand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.enuns.Status;
import com.tester.repository.TestSuiteRepository;
import com.tester.service.RelatorioJiraService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uses the actual Excel report so the preview and Jira comment contain the same fields. */
@Service
public class SuiteCommentService {
    private final DemandRepository demands;
    private final TestSuiteRepository suites;
    private final RelatorioJiraService reports;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired private com.tester.service.CaseMediaService media;
    @org.springframework.beans.factory.annotation.Autowired private com.tester.integration.jira.client.JiraClient jira;
    @org.springframework.beans.factory.annotation.Autowired private com.tester.service.CaseContentService caseContent;
    public SuiteCommentService(DemandRepository demands, TestSuiteRepository suites, RelatorioJiraService reports, ObjectMapper mapper) {
        this.demands=demands; this.suites=suites; this.reports=reports; this.mapper=mapper;
    }
    public record Preview(String title, List<List<String>> rows, int cases) {}

    /** Linha do Excel onde começa a listagem de casos; acima dela vai o cabeçalho da suíte. */
    private static final int PRIMEIRA_LINHA_DE_CASO = 6;

    @Transactional(readOnly=true)
    public Preview preview(String key, Long suiteId, Set<Status> statusFiltro) {
        var demand=demands.findById(key).orElseThrow(() -> new IllegalArgumentException("Demanda não encontrada."));
        var suite=suites.findById(suiteId).orElseThrow(() -> new IllegalArgumentException("Suíte não encontrada."));
        if(demand.testPlan==null || suite.getTestPlan()==null ||
           !Objects.equals(demand.testPlan.getId(),suite.getTestPlan().getId()) ||
           !key.equalsIgnoreCase(suite.getTestPlan().getJira()))
            throw new IllegalArgumentException("A suíte deve pertencer ao plano vinculado a esta demanda Jira.");
        var filtro=statusFiltro==null?Set.<Status>of():statusFiltro;
        String title="Relatório de testes · Suíte "+suite.getCodeSuite()+" · "+key
            +(filtro.isEmpty()?"":" · "+descricao(filtro));
        var rows=new ArrayList<List<String>>();
        int characters=title.length();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(reports.gerarRelatorioJiraExcel(suiteId,filtro)))) {
            var formatter=new DataFormatter(Locale.forLanguageTag("pt-BR"));
            for(var row:workbook.getSheetAt(0)) {
                var cells=new ArrayList<String>();
                for(int column=0;column<5;column++) {
                    String value=formatter.formatCellValue(row.getCell(column));
                    characters+=value.length()+1;
                    cells.add(value);
                }
                rows.add(cells);
            }
        } catch(IOException error) {
            throw new IllegalStateException("Não foi possível preparar o conteúdo do Excel da suíte.",error);
        }
        int cases=Math.max(0,rows.size()-PRIMEIRA_LINHA_DE_CASO);
        // Sem nenhum caso a listar o comentário viraria só o cabeçalho da suíte, o
        // que no Jira é indistinguível de um envio que deu errado. Melhor recusar
        // aqui, dizendo o motivo, do que publicar uma tabela vazia.
        if(cases==0)
            throw new IllegalArgumentException(filtro.isEmpty()
                ? "Esta suíte não tem casos de teste para enviar."
                : "Nenhum caso desta suíte está em "+descricao(filtro)+". Escolha outro status ou outra suíte.");
        if(characters>30000) throw new IllegalArgumentException("O conteúdo da suíte excede o limite de um comentário no Jira (30 mil caracteres). Divida os casos em suítes menores, filtre por status ou desmarque o envio. Nada foi enviado.");
        return new Preview(title,rows,cases);
    }

    @Transactional(readOnly=true)
    public JsonNode document(String key, Long suiteId, Set<Status> statusFiltro) {
        var preview=preview(key,suiteId,statusFiltro);
        var doc=mapper.createObjectNode().put("type","doc").put("version",1);
        var content=doc.putArray("content");
        content.add(paragraph(preview.title()));
        var table=content.addObject().put("type","table");
        table.putObject("attrs").put("isNumberColumnEnabled",false).put("layout","default");
        var body=table.putArray("content");
        for(int i=0;i<preview.rows().size();i++) {
            var row=body.addObject().put("type","tableRow").putArray("content");
            int column=0;
            for(String value:preview.rows().get(i)) {
                var cell=row.addObject().put("type",i==2 || i==5 ? "tableHeader":"tableCell");
                boolean summary = (i==0 && column>=2) || (i==1 && column==2);
                cell.putArray("content").add(summary || (i>=PRIMEIRA_LINHA_DE_CASO && column==4)?resultParagraph(value):paragraph(value));
                column++;
            }
        }
        var suite=suites.findById(suiteId).orElseThrow();
        var selected=suite.getCases().stream().filter(c -> statusFiltro==null || statusFiltro.isEmpty() || statusFiltro.contains(c.getStatus()))
            .sorted(java.util.Comparator.comparing(com.tester.entity.TestCaseEntity::getId)).toList();
        if(media==null)return doc;
        var used=new java.util.LinkedHashMap<java.util.UUID,com.tester.service.CaseMediaService.View>();
        for(int index=0;index<selected.size();index++) {
            var testCase=selected.get(index);var files=media.list(testCase.getId());
            var cells=(com.fasterxml.jackson.databind.node.ArrayNode)body.get(PRIMEIRA_LINHA_DE_CASO+index).path("content");
            var ordered=caseContent==null?null:caseContent.read(testCase.getId());
            for(String field:List.of("expected","obtained")) {
                int column=field.equals("expected")?1:2;
                var blocks=((com.fasterxml.jackson.databind.node.ObjectNode)cells.get(column)).putArray("content");
                if(ordered!=null) {
                    for(var block:ordered.path(field)) {
                        if("text".equals(block.path("type").asText()))blocks.add(paragraph(block.path("text").asText()));
                        else if("image".equals(block.path("type").asText()))files.stream().filter(f->f.id().toString().equals(block.path("mediaId").asText()) && f.field().equals(field)).findFirst().ifPresent(f->{blocks.add(mediaBlock(f));used.put(f.id(),f);});
                    }
                } else {
                    blocks.add(paragraph(field.equals("expected")?testCase.getExpectedResult():testCase.getObtainedResult()));
                    for(var f:files)if(f.field().equals(field)){blocks.add(mediaBlock(f));used.put(f.id(),f);}
                }
                if(blocks.isEmpty())blocks.add(paragraph(""));
            }
            var evidence=(com.fasterxml.jackson.databind.node.ArrayNode)cells.get(3).path("content");
            for(var f:files)if(f.field().equals("video")){evidence.add(mediaBlock(f));used.put(f.id(),f);}
        }
        if(doc.toString().length()>100000)throw new IllegalArgumentException("O comentário formatado é muito grande. Divida a suíte antes de enviar. Nada foi enviado.");
        var ids=new java.util.HashMap<String,String>();
        for(var f:used.values())ids.put(f.id().toString(),jira.attachmentMedia(key,f.name(),f.mime(),media.bytes(f.caseId(),f.id())));
        replaceMediaIds(doc,ids);
        return doc;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode mediaBlock(com.tester.service.CaseMediaService.View file) {
        int width=320,height=180;
        if(file.mime().startsWith("image/"))try {
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(media.bytes(file.caseId(),file.id())));
            if(image!=null){width=image.getWidth();height=image.getHeight();}
        }catch(Exception ignored) { /* The Jira media service also reads the original dimensions. */ }
        var node=mapper.createObjectNode().put("type","mediaSingle");node.putObject("attrs").put("layout","align-start");
        var attrs=node.putArray("content").addObject().put("type","media").putObject("attrs");
        attrs.put("id",file.id().toString()).put("type","file").put("collection","").put("alt",file.name()).put("width",240).put("height",Math.max(1,(int)Math.round(240.0*height/width)));
        return node;
    }
    private void replaceMediaIds(JsonNode node,java.util.Map<String,String> ids) {
        if("media".equals(node.path("type").asText())) {
            String id=ids.get(node.path("attrs").path("id").asText());
            if(id==null || id.isBlank())throw new IllegalStateException("O Jira não confirmou a mídia para o corpo do comentário.");
            ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("attrs")).put("id",id);
        }
        for(var child:node.path("content"))replaceMediaIds(child,ids);
    }

    private static String wikiText(String text) {
        return text==null?"":text.replace("\\","\\\\").replace("|","\\|").replace("!","\\!").replace("[","\\[").replace("]","\\]").replace("\r","").replace("\n","\\\\");
    }
    private JsonNode resultParagraph(String value) {
        var node=paragraph(value);
        String color=value!=null && value.startsWith("Falhou")?"#AE2E24":value!=null && value.startsWith("Aprovado")?"#216E4E":value!=null && value.startsWith("Impedimento")?"#FFFF00":value!=null && (value.equals("em progresso") || value.startsWith("Em progresso"))?"#0055CC":null;
        if(color!=null)for(var text:node.path("content"))if("text".equals(text.path("type").asText())) {
            var marks=((com.fasterxml.jackson.databind.node.ObjectNode)text).putArray("marks");
            marks.addObject().put("type","strong");
            marks.addObject().put("type","textColor").putObject("attrs").put("color",color);
        }
        return node;
    }
    private String descricao(Set<Status> statusFiltro) {
        return statusFiltro.stream().map(Status::getDescricao).sorted().reduce((a,b)->a+", "+b).orElse("");
    }

    /** Endereço solto no meio do texto — é assim que a evidência do Vimeo chega. */
    private static final java.util.regex.Pattern ENDERECO =
        java.util.regex.Pattern.compile("https?://[^\\s<>\"]+");

    /** Pontuação que costuma encostar no fim de um endereço e não faz parte dele. */
    private static final String PONTUACAO_FINAL = ".,;:!?)]}";

    /**
     * Um parágrafo com `content` vazio não é ADF válido, e célula vazia é comum
     * (a planilha tem linhas de duas colunas). Nesses casos o parágrafo sai sem o
     * campo `content`, que é a forma correta de representar vazio.
     */
    private JsonNode paragraph(String text) {
        var paragraph=mapper.createObjectNode().put("type","paragraph");
        var lines=text==null?new String[0]:text.split("\\R",-1);
        boolean vazio=true;
        for(String line:lines)if(!line.isEmpty())vazio=false;
        if(vazio)return paragraph;
        var content=paragraph.putArray("content");
        for(int i=0;i<lines.length;i++) {
            if(i>0)content.addObject().put("type","hardBreak");
            if(!lines[i].isEmpty())escrever(content,lines[i]);
        }
        return paragraph;
    }

    /**
     * Escreve uma linha reconhecendo endereços dentro dela.
     *
     * Texto puro chega ao Jira como texto puro: a evidência do Vimeo só virava
     * link depois que alguém editava o comentário e dava um espaço, porque é a
     * edição que dispara o reconhecimento automático. Marcando o endereço na
     * origem, ele já nasce clicável.
     *
     * Endereço sozinho na célula vira `inlineCard` — o cartão que o Jira monta
     * com o ícone e o título da gravação. No meio de um texto isso quebraria a
     * leitura, então ali ele vira o próprio texto com a marca `link`.
     */
    private void escrever(com.fasterxml.jackson.databind.node.ArrayNode content, String line) {
        var busca=ENDERECO.matcher(line);
        boolean sozinho=line.trim().matches("https?://[^\\s<>\"]+");
        int posicao=0;
        while(busca.find()) {
            String antes=line.substring(posicao,busca.start());
            if(!antes.isEmpty())content.addObject().put("type","text").put("text",antes);

            String endereco=aparar(busca.group());
            if(sozinho)content.addObject().put("type","inlineCard").putObject("attrs").put("url",endereco);
            else {
                var texto=content.addObject().put("type","text").put("text",endereco);
                texto.putArray("marks").addObject().put("type","link").putObject("attrs").put("href",endereco);
            }
            posicao=busca.start()+endereco.length();
        }
        String resto=line.substring(posicao);
        if(!resto.isEmpty())content.addObject().put("type","text").put("text",resto);
    }

    /** Devolve ao texto a pontuação que encostou no fim do endereço. */
    private static String aparar(String endereco) {
        int fim=endereco.length();
        while(fim>0 && PONTUACAO_FINAL.indexOf(endereco.charAt(fim-1))>=0)fim--;
        return fim==0?endereco:endereco.substring(0,fim);
    }
}
