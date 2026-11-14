package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.demand.*;
import com.tester.entity.*;
import com.tester.enuns.Status;
import com.tester.repository.TestSuiteRepository;
import com.tester.service.RelatorioJiraService;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.config.*;
import com.tester.integration.jira.client.JiraClient;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuiteCommentTest {
    final ObjectMapper mapper=new ObjectMapper();
    final DemandRepository demands=mock(DemandRepository.class);
    final TestSuiteRepository suites=mock(TestSuiteRepository.class);
    final TestPlan plan=new TestPlan();
    final TestSuite suite=new TestSuite();
    final Demand demand=new Demand();
    static final Set<Status> TODOS=Set.of();
    SuiteCommentTest() {
        plan.setId(7L);plan.setJira("QA-42");plan.setName("Validar login");
        plan.setCallNumber("123");plan.setMatriz("Teste");plan.setUserName("usuario-teste");plan.setPassword("senha-teste");
        suite.setId(9L);suite.setCodeSuite(2L);suite.setTestPlan(plan);suite.setData(LocalDate.of(2026,9,14));
        demand.jiraKey="QA-42";demand.testPlan=plan;
        when(demands.findById("QA-42")).thenReturn(Optional.of(demand));
        when(suites.findById(9L)).thenReturn(Optional.of(suite));
    }
    SuiteCommentService service(){return new SuiteCommentService(demands,suites,new RelatorioJiraService(suites),mapper);}
    static TestCaseEntity caso(long id,String cenario,Status status) {
        var entity=new TestCaseEntity();entity.setId(id);entity.setScenario(cenario);entity.setStatus(status);return entity;
    }
    @Test void containsActualExcelFieldsAndMultilineCasesInDeterministicOrder() {
        var a=new TestCaseEntity();a.setId(1L);a.setScenario("Login\ncom acentuação & <texto>");a.setExpectedResult("Entrar");a.setObtainedResult("Entrou");a.setVideoEvidence("https://vimeo.com/123");
        var b=new TestCaseEntity();b.setId(2L);b.setScenario("Sair");
        suite.setCases(new HashSet<>(List.of(b,a)));
        var preview=service().preview("QA-42",9L,TODOS);
        assertThat(preview.rows()).hasSize(8);
        assertThat(preview.cases()).isEqualTo(2);
        assertThat(preview.rows().get(0).get(1)).isEqualTo("14/09/2026");
        assertThat(preview.rows().get(3)).containsExactly("QA-42","123","Teste","usuario-teste","senha-teste");
        assertThat(preview.rows().get(6)).containsExactly(a.getScenario(),"Entrar","Entrou","https://vimeo.com/123","EM_PROGRESSO");
        var doc=service().document("QA-42",9L,TODOS);
        assertThat(doc.path("type").asText()).isEqualTo("doc");
        assertThat(doc.path("version").asInt()).isEqualTo(1);
        assertThat(doc.at("/content/1/content/5/content/0/type").asText()).isEqualTo("tableHeader");
        assertThat(doc.toString()).contains("hardBreak","acentuação & <texto>","senha-teste");
    }
    /**
     * Célula vazia é comum: as primeiras linhas da planilha só ocupam duas
     * colunas. Um parágrafo com content vazio não é ADF válido, então o
     * parágrafo precisa sair sem o campo — e não com uma lista vazia.
     */
    @Test void emptyCellBecomesParagraphWithoutContentInsteadOfEmptyArray() {
        suite.setCases(Set.of(caso(1L,"Único",Status.CONCLUIDA)));
        var doc=service().document("QA-42",9L,TODOS);
        assertThat(doc.toString()).doesNotContain("\"content\":[]");
        var primeiraLinha=doc.at("/content/1/content/0/content");
        assertThat(primeiraLinha.get(2).at("/content/0/content/0/text").asText()).isEqualTo("Falhou: 0% (0)");
        // The empty obtained-result cell still uses a valid empty ADF paragraph.
        assertThat(doc.at("/content/1/content/6/content/2/content/0").has("content")).isFalse();
        assertThat(primeiraLinha.get(0).at("/content/0/content/0/text").asText()).isEqualTo("Data");
    }
    /**
     * A evidência do Vimeo ocupa a célula inteira, então vira o cartão que o Jira
     * monta com ícone e título. Antes ia como texto puro e só virava link se
     * alguém editasse o comentário e desse um espaço.
     */
    @Test void evidenceUrlBecomesAClickableCard() {
        var caso = caso(1L, "Gravação", Status.RETORNO);
        caso.setVideoEvidence("https://vimeo.com/1226964093/12293c01c4?share=copy&fl=sv&fe=ci");
        suite.setCases(Set.of(caso));

        var doc = service().document("QA-42", 9L, TODOS);
        var celula = doc.at("/content/1/content/6/content/3/content/0/content/0");
        assertThat(celula.path("type").asText()).isEqualTo("inlineCard");
        assertThat(celula.at("/attrs/url").asText())
            .isEqualTo("https://vimeo.com/1226964093/12293c01c4?share=copy&fl=sv&fe=ci");
    }

    /** No meio de uma frase o cartão atrapalharia a leitura; ali basta o link. */
    @Test void urlInsideSentenceBecomesLinkedText() {
        var caso = caso(1L, "Cenário", Status.RETORNO);
        caso.setObtainedResult("Falhou, veja https://vimeo.com/123456. Repetir depois.");
        suite.setCases(Set.of(caso));

        var doc = service().document("QA-42", 9L, TODOS);
        var pedacos = doc.at("/content/1/content/6/content/2/content/0/content");
        assertThat(pedacos.get(0).path("text").asText()).isEqualTo("Falhou, veja ");
        assertThat(pedacos.get(1).path("text").asText()).isEqualTo("https://vimeo.com/123456");
        assertThat(pedacos.get(1).at("/marks/0/type").asText()).isEqualTo("link");
        assertThat(pedacos.get(1).at("/marks/0/attrs/href").asText()).isEqualTo("https://vimeo.com/123456");
        // O ponto final encostado no endereço é texto, não faz parte do link.
        assertThat(pedacos.get(2).path("text").asText()).isEqualTo(". Repetir depois.");
    }

    /** Texto sem endereço nenhum continua saindo como saía. */
    @Test void plainTextIsUntouched() {
        var caso = caso(1L, "Sem link", Status.RETORNO);
        caso.setObtainedResult("Tudo certo");
        suite.setCases(Set.of(caso));

        var celula = service().document("QA-42", 9L, TODOS).at("/content/1/content/6/content/2/content/0/content");
        assertThat(celula).hasSize(1);
        assertThat(celula.get(0).path("type").asText()).isEqualTo("text");
        assertThat(celula.get(0).has("marks")).isFalse();
    }

    @Test void keepsOnlyCasesInTheChosenStatus() {
        suite.setCases(new HashSet<>(List.of(
            caso(1L,"Concluído",Status.CONCLUIDA),
            caso(2L,"Devolvido",Status.RETORNO),
            caso(3L,"Andando",Status.EM_PROGRESSO))));

        var retorno=service().preview("QA-42",9L,Set.of(Status.RETORNO));
        assertThat(retorno.cases()).isEqualTo(1);
        assertThat(retorno.rows().get(6).get(0)).isEqualTo("Devolvido");
        assertThat(retorno.title()).contains("Falhou");

        var concluida=service().preview("QA-42",9L,Set.of(Status.CONCLUIDA));
        assertThat(concluida.cases()).isEqualTo(1);
        assertThat(concluida.rows().get(6).get(0)).isEqualTo("Concluído");

        var dois=service().preview("QA-42",9L,Set.of(Status.CONCLUIDA,Status.RETORNO));
        assertThat(dois.cases()).isEqualTo(2);

        assertThat(service().preview("QA-42",9L,TODOS).cases()).isEqualTo(3);
    }
    /** Comentário só com o cabeçalho da suíte não diz nada; melhor recusar dizendo o motivo. */
    @Test void refusesWhenTheChosenStatusLeavesNoCase() {
        suite.setCases(Set.of(caso(1L,"Concluído",Status.CONCLUIDA)));
        assertThatThrownBy(()->service().document("QA-42",9L,Set.of(Status.IMPEDIMENTO)))
            .hasMessageContaining("Nenhum caso desta suíte está em Impedimento");
        suite.setCases(Set.of());
        assertThatThrownBy(()->service().document("QA-42",9L,TODOS))
            .hasMessageContaining("não tem casos de teste");
    }
    @Test void rejectsDifferentPlanEvenWithSameJiraKeyBeforeExport() throws Exception {
        var other=new TestPlan();other.setId(8L);other.setJira("QA-42");suite.setTestPlan(other);
        var report=mock(RelatorioJiraService.class);
        var service=new SuiteCommentService(demands,suites,report,mapper);
        assertThatThrownBy(()->service.document("QA-42",9L,TODOS)).hasMessageContaining("plano vinculado");
        verifyNoInteractions(report);
    }
    @Test void rejectsWrongJiraAndMissingPlan() {
        plan.setJira("QA-99");
        assertThatThrownBy(()->service().document("QA-42",9L,TODOS)).hasMessageContaining("plano vinculado");
        demand.testPlan=null;
        assertThatThrownBy(()->service().document("QA-42",9L,TODOS)).hasMessageContaining("plano vinculado");
    }
    @Test void rejectsMissingSuite() {
        assertThatThrownBy(()->service().document("QA-42",99L,TODOS)).hasMessageContaining("não encontrada");
    }
    @Test void rejectsOversizedCommentWithoutTruncating() {
        var a=new TestCaseEntity();a.setId(1L);a.setScenario("x".repeat(30100));suite.setCases(Set.of(a));
        assertThatThrownBy(()->service().document("QA-42",9L,TODOS)).hasMessageContaining("Nada foi enviado");
    }
    /**
     * O comentário vai pelo recurso próprio de comentários, não embutido na
     * transição: uma transição só aceita comment se a tela dela tiver esse
     * campo, e quando não tem o Jira muda o status e descarta o texto.
     */
    @Test void publishesCommentThroughTheCommentResource() {
        var settings=mock(IntegrationService.class);var config=new IntegrationSetting();config.baseUrl="http://127.0.0.1:9999";config.username="fixture";
        when(settings.setting("jira")).thenReturn(config);when(settings.secret("jira")).thenReturn("fixture");
        var transport=mock(JsonHttpTransport.class);
        when(transport.request(anyString(),anyMap(),any(),anyInt(),anyString()))
            .thenReturn(mapper.createObjectNode().put("id","10101"));
        var client=new JiraClient(settings,mapper,transport);
        suite.setCases(Set.of(caso(1L,"Único",Status.RETORNO)));
        var doc=service().document("QA-42",9L,Set.of(Status.RETORNO));

        assertThat(client.comment("QA-42",doc)).isEqualTo("10101");

        var payload=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(transport).request(eq(config.baseUrl+"/rest/api/3/issue/QA-42/comment"),anyMap(),payload.capture(),eq(30000),eq("Jira"));
        assertThat(mapper.valueToTree(payload.getValue()).path("body")).isEqualTo(doc);
        verifyNoMoreInteractions(transport);
    }
    /** Sem o id do comentário criado não dá para afirmar que foi publicado. */
    @Test void refusesToReportSuccessWhenJiraDoesNotReturnTheComment() {
        var settings=mock(IntegrationService.class);var config=new IntegrationSetting();config.baseUrl="http://127.0.0.1:9999";config.username="fixture";
        when(settings.setting("jira")).thenReturn(config);when(settings.secret("jira")).thenReturn("fixture");
        var transport=mock(JsonHttpTransport.class);
        when(transport.request(anyString(),anyMap(),any(),anyInt(),anyString())).thenReturn(mapper.createObjectNode());
        var client=new JiraClient(settings,mapper,transport);
        assertThatThrownBy(()->client.comment("QA-42",mapper.createObjectNode()))
            .hasMessageContaining("não devolveu o comentário criado");
    }
    /** A transição continua sendo um POST só de status, sem update. */
    @Test void transitionCarriesOnlyTheStatusChange() {
        var settings=mock(IntegrationService.class);var config=new IntegrationSetting();config.baseUrl="http://127.0.0.1:9999";config.username="fixture";
        when(settings.setting("jira")).thenReturn(config);when(settings.secret("jira")).thenReturn("fixture");
        var transport=mock(JsonHttpTransport.class);
        var client=new JiraClient(settings,mapper,transport);
        client.transition("QA-42","31");
        var payload=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(transport).request(eq(config.baseUrl+"/rest/api/3/issue/QA-42/transitions"),anyMap(),payload.capture(),eq(30000),eq("Jira"));
        var json=mapper.valueToTree(payload.getValue());
        assertThat(json.at("/transition/id").asText()).isEqualTo("31");
        assertThat(json.has("update")).isFalse();
    }
    @Test void driveEvidenceRemainsClickableInJiraAndExcel() throws Exception {
        var c=caso(1L,"Drive",Status.CONCLUIDA);
        String url="https://drive.google.com/file/d/video_123/view?resourcekey=key&usp=sharing";
        c.setVideoEvidence(url);suite.setCases(Set.of(c));
        var document=service().document("QA-42",9L,TODOS);
        assertThat(document.at("/content/1/content/6/content/3/content/0/content/0/attrs/url").asText()).isEqualTo(url);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(new RelatorioJiraService(suites).gerarRelatorioJiraExcel(9L,TODOS)))) {
            assertThat(workbook.getSheetAt(0).getRow(6).getCell(3).getHyperlink().getAddress()).isEqualTo(url);
        }
    }
    @Test void sendsOnlySelectedCaseMediaWithThumbnailsAndVideoAfterEvidence() {
        var a=caso(1L,"Retorno",Status.RETORNO);a.setVideoEvidence("https://vimeo.com/123");
        var b=caso(2L,"Concluído",Status.CONCLUIDA);suite.setCases(Set.of(b,a));
        var service=service();
        var media=mock(com.tester.service.CaseMediaService.class);
        var jira=mock(JiraClient.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"media",media);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"jira",jira);
        var image=new com.tester.service.CaseMediaService.View(UUID.randomUUID(),1L,"expected","image/png","CT-1-expected.png",2L);
        var video=new com.tester.service.CaseMediaService.View(UUID.randomUUID(),1L,"video","video/webm","CT-1-video.webm",2L);
        when(media.list(1L)).thenReturn(List.of(image,video));
        when(media.bytes(eq(1L),any())).thenReturn(new byte[]{1,2});
        when(jira.attachmentMedia(eq("QA-42"),anyString(),anyString(),any())).thenReturn("11111111-1111-4111-8111-111111111111");
        var doc=service.document("QA-42",9L,Set.of(Status.RETORNO));
        assertThat(doc.at("/content/1/content/6/content/1/content/1/type").asText()).isEqualTo("mediaSingle");
        assertThat(doc.at("/content/1/content/6/content/3/content/0/content/0/attrs/url").asText()).isEqualTo("https://vimeo.com/123");
        assertThat(doc.at("/content/1/content/6/content/3/content/1/type").asText()).isEqualTo("mediaSingle");
        assertThat(doc.toString()).doesNotContain("Concluído","qaWiki");
        verify(media,never()).list(2L);
        verify(jira).attachmentMedia(eq("QA-42"),eq(image.name()),eq(image.mime()),any());
        verify(jira).attachmentMedia(eq("QA-42"),eq(video.name()),eq(video.mime()),any());
    }
    @Test void preservesTextImageTextImageTextOrderInJiraBody() throws Exception {
        var a=caso(1L,"Intercalado",Status.RETORNO);suite.setCases(Set.of(a));
        var service=service();var media=mock(com.tester.service.CaseMediaService.class);var jira=mock(JiraClient.class);var content=mock(com.tester.service.CaseContentService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"media",media);org.springframework.test.util.ReflectionTestUtils.setField(service,"jira",jira);org.springframework.test.util.ReflectionTestUtils.setField(service,"caseContent",content);
        var one=new com.tester.service.CaseMediaService.View(UUID.randomUUID(),1,"expected","image/png","first.png",2);
        var two=new com.tester.service.CaseMediaService.View(UUID.randomUUID(),1,"expected","image/png","second.png",2);
        when(media.list(1)).thenReturn(List.of(two,one));when(media.bytes(eq(1L),any())).thenReturn(new byte[]{1,2});
        var ordered=mapper.createObjectNode();var blocks=ordered.putArray("expected");
        blocks.addObject().put("type","text").put("text","Erro encontrado conforme a imagem");blocks.addObject().put("type","image").put("mediaId",one.id().toString());blocks.addObject().put("type","text").put("text","Depois do primeiro print");blocks.addObject().put("type","image").put("mediaId",two.id().toString());blocks.addObject().put("type","text").put("text","Conclusão");ordered.putArray("obtained");
        when(content.read(1)).thenReturn(ordered);when(jira.attachmentMedia(eq("QA-42"),anyString(),anyString(),any())).thenReturn("11111111-1111-4111-8111-111111111111");
        var result=service.document("QA-42",9L,Set.of());var cell=result.at("/content/1/content/6/content/1/content");
        assertThat(cell).hasSize(5);assertThat(cell.get(0).at("/content/0/text").asText()).isEqualTo("Erro encontrado conforme a imagem");assertThat(cell.get(1).path("type").asText()).isEqualTo("mediaSingle");assertThat(cell.get(2).at("/content/0/text").asText()).isEqualTo("Depois do primeiro print");assertThat(cell.get(3).path("type").asText()).isEqualTo("mediaSingle");assertThat(cell.get(4).at("/content/0/text").asText()).isEqualTo("Conclusão");
    }
    @Test void reportLabelsAndJiraResultColorsAreReadable() {
        suite.setCases(Set.of(caso(1L,"Cenário com falha",Status.RETORNO),caso(2L,"Cenário aprovado",Status.CONCLUIDA)));
        var service=service();var preview=service.preview("QA-42",9L,TODOS);
        assertThat(preview.rows().get(2)).containsExactly("Projeto","Chamado","Matriz","Usuário","Senha");
        assertThat(preview.rows().get(5).get(3)).isEqualTo("Evidência");
        assertThat(preview.rows().get(6).get(4)).isEqualTo("Falhou");assertThat(preview.rows().get(7).get(4)).isEqualTo("Aprovado");
        var doc=service.document("QA-42",9L,TODOS);
        assertThat(doc.at("/content/1/content/6/content/4/content/0/content/0/text").asText()).isEqualTo("Falhou");
        assertThat(doc.at("/content/1/content/6/content/4/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#AE2E24");
        assertThat(doc.at("/content/1/content/7/content/4/content/0/content/0/text").asText()).isEqualTo("Aprovado");
        assertThat(doc.at("/content/1/content/7/content/4/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#216E4E");
    }
    @Test void summaryShowsWholeSuitePercentagesEvenWhenDetailsAreFiltered() {
        suite.setCases(Set.of(caso(1L,"Falha",Status.RETORNO),caso(2L,"Bloqueado",Status.IMPEDIMENTO),caso(3L,"Andamento",Status.EM_PROGRESSO),caso(4L,"Passou",Status.CONCLUIDA)));
        var service=service();var preview=service.preview("QA-42",9L,Set.of(Status.RETORNO));
        assertThat(preview.cases()).isEqualTo(1);
        assertThat(preview.rows().get(0).subList(2,5)).containsExactly("Falhou: 25% (1)","Impedimento: 25% (1)","Em progresso: 25% (1)");
        assertThat(preview.rows().get(1).subList(2,5)).containsExactly("Aprovado: 25% (1)","Total da suíte: 4 CTs","Detalhes enviados: 1 CT");
        var doc=service.document("QA-42",9L,Set.of(Status.RETORNO));
        assertThat(doc.at("/content/1/content/0/content/3/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#0055CC");
        assertThat(doc.at("/content/1/content/0/content/4/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#0055CC");
        assertThat(doc.at("/content/1/content/1/content/2/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#216E4E");
    }
    @Test void impedimentAndInProgressRowsAreBlue() {
        suite.setCases(Set.of(caso(1L,"Bloqueado",Status.IMPEDIMENTO),caso(2L,"Andamento",Status.EM_PROGRESSO)));
        var doc=service().document("QA-42",9L,Set.of());
        for(int row:new int[]{6,7})assertThat(doc.at("/content/1/content/"+row+"/content/4/content/0/content/0/marks/1/attrs/color").asText()).isEqualTo("#0055CC");
    }
}
