package com.tester;

import com.tester.entity.*;
import com.tester.enuns.Status;
import com.tester.integration.ai.AiService;
import com.tester.integration.ai.TestCaseAiProvider;
import com.tester.repository.TestSuiteRepository;
import com.tester.service.SuiteDocService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Documentação a partir de uma suíte.
 *
 * O que não pode se perder em nenhuma das três saídas é a gravação do Vimeo: é a
 * evidência que explica o passo melhor do que o texto, e some fácil quando o
 * conteúdo passa por uma reescrita.
 */
class SuiteDocTest {

    final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    final AiService ai = mock(AiService.class);
    final TestCaseAiProvider provider = mock(TestCaseAiProvider.class);
    final SuiteDocService service = new SuiteDocService(suites, ai);

    final TestPlan plan = new TestPlan();
    final TestSuite suite = new TestSuite();

    static final String GRAVACAO = "https://vimeo.com/1226964093/12293c01c4?share=copy";

    SuiteDocTest() {
        SystemModule fiscal = new SystemModule();
        fiscal.setId(1L);
        fiscal.setName("Fiscal");
        plan.setId(7L);
        plan.setName("Retirar obrigatoriedade de ICMS");
        plan.setJira("SOP-260");
        plan.setSystemModule(fiscal);
        suite.setId(9L);
        suite.setCodeSuite(1L);
        suite.setTestPlan(plan);
        suite.setData(LocalDate.of(2026, 9, 16));
        when(suites.findById(9L)).thenReturn(Optional.of(suite));
        when(ai.resolverProvider(anyString())).thenReturn("groq");
        when(ai.resolverProvider(null)).thenReturn("groq");
        // A IA local segue outro caminho dentro do serviço, e é por este nome
        // que ela é reconhecida.
        when(ai.resolverProvider("ollama")).thenReturn("ollama");
        when(ai.provider("groq")).thenReturn(provider);
        when(ai.provider("ollama")).thenReturn(provider);
    }

    static TestCaseEntity caso(long id, long codigo, String cenario, String gravacao) {
        TestCaseEntity caso = new TestCaseEntity();
        caso.setId(id);
        caso.setCodeCase(codigo);
        caso.setScenario(cenario);
        caso.setExpectedResult("Esperado de " + cenario);
        caso.setObtainedResult("Obtido de " + cenario);
        caso.setStatus(Status.CONCLUIDA);
        caso.setVideoEvidence(gravacao);
        return caso;
    }

    // ── Conteúdo exato ────────────────────────────────────────────────────────

    @Test void exactDocCarriesEveryFieldAndTheRecordingAsLink() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));

        SuiteDocService.Documento doc = service.exato(9L);

        assertThat(doc.format()).isEqualTo("markdown");
        assertThat(doc.title()).isEqualTo("Retirar obrigatoriedade de ICMS");
        // O editor do card guarda HTML; o texto puro serve para exportar.
        assertThat(doc.html())
                .contains("<h2>CT-1 — Emitir nota</h2>")
                .contains("<a href=\"" + GRAVACAO + "\"")
                .doesNotContain("<html>");
        assertThat(doc.content())
                .contains("SOP-260")
                .contains("Fiscal")
                .contains("CT-1 — Emitir nota")
                .contains("Esperado de Emitir nota")
                .contains("Obtido de Emitir nota")
                .contains("Aprovado")
                // A gravação vira link, não some nem fica texto cru.
                .contains("[" + GRAVACAO + "](" + GRAVACAO + ")");
        verifyNoInteractions(provider);
    }

    /**
     * A gravação vira o player, como no caso de teste. Sair da página para
     * assistir significa, na prática, não assistir.
     */
    @Test void recordingBecomesAnEmbeddedPlayer() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));

        String html = service.exato(9L).html();

        assertThat(html)
                .contains("data-resizable-video")
                .contains("data-src=\"https://player.vimeo.com/video/1226964093?h=12293c01c4\"")
                .contains("<iframe class=\"vimeo-player\"")
                // O endereço original continua ali, para abrir no Vimeo.
                .contains("<a href=\"" + GRAVACAO + "\"");
    }

    /** Endereço que não é do Vimeo não vira player — segue como link. */
    @Test void nonVimeoEvidenceStaysALink() {
        TestCaseEntity caso = caso(1L, 1L, "Emitir nota", "https://exemplo.test/video.mp4");
        suite.setCases(new HashSet<>(List.of(caso)));

        String html = service.exato(9L).html();
        assertThat(html).doesNotContain("data-resizable-video");
        assertThat(html).contains("<a href=\"https://exemplo.test/video.mp4\"");
    }

    /** Sem HTTPS não há player: o iframe não pode virar porta para qualquer coisa. */
    @Test void insecureUrlIsNeverEmbedded() {
        assertThat(SuiteDocService.playerDoVimeo("http://vimeo.com/123456")).isNull();
        assertThat(SuiteDocService.playerDoVimeo("https://vimeo.com/abc")).isNull();
        assertThat(SuiteDocService.playerDoVimeo("https://outro.test/123456")).isNull();
        assertThat(SuiteDocService.playerDoVimeo("https://vimeo.com/123456"))
                .isEqualTo("https://player.vimeo.com/video/123456");
    }

    @Test void exactDocKeepsTheCaseOrder() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 2L, "Segundo", null), caso(2L, 1L, "Primeiro", null))));

        String conteudo = service.exato(9L).content();
        assertThat(conteudo.indexOf("Primeiro")).isLessThan(conteudo.indexOf("Segundo"));
    }

    /** Suíte sem casos ainda gera o cabeçalho — serve de esqueleto para preencher. */
    @Test void exactDocOfEmptySuiteIsJustTheHeader() {
        suite.setCases(new HashSet<>());
        assertThat(service.exato(9L).content()).contains("Retirar obrigatoriedade de ICMS", "SOP-260");
    }

    // ── Documentação por IA: IAs de nuvem ─────────────────────────────────────

    /**
     * Com um modelo de nuvem o documento é pedido inteiro, numa chamada só: ele
     * enxerga o conjunto, agrupa os assuntos e encadeia o texto. Só a IA local
     * precisa da pergunta fatiada, e essa limitação não pode rebaixar o que as
     * outras entregam.
     */
    @Test void cloudModelsWriteTheWholeDocumentInOneCall() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", GRAVACAO), caso(2L, 2L, "Cancelar nota", null))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("<h2>Emissão</h2><p>Como emitir.</p><p>[[VIDEO-1]]</p><h2>Cancelamento</h2><p>Fim.</p>");

        SuiteDocService.Documento doc = service.comIa(9L, "groq");

        verify(provider, times(1)).texto(anyString(), anyInt());
        assertThat(doc.format()).isEqualTo("html");
        assertThat(doc.html()).contains("<h2>Emissão</h2>", "<p>Como emitir.</p>", "<h2>Cancelamento</h2>");
    }

    /** O marcador vira o player, com a legenda do assunto, onde o modelo o pôs. */
    @Test void theMarkerBecomesThePlayerWhereTheModelPutIt() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("<h2>Emissão</h2><p>Como emitir.</p><p>[[VIDEO-1]]</p><p>Depois.</p>");

        String html = service.comIa(9L, "groq").html();

        assertThat(html)
                .contains("<p><em>Demonstração: Emitir nota</em></p>")
                .contains("data-src=\"https://player.vimeo.com/video/1226964093?h=12293c01c4\"")
                .doesNotContain("[[VIDEO-1]]")
                .doesNotContain("<h2>Demonstrações</h2>")
                // Fora do parágrafo: vídeo dentro de <p> o editor desmonta.
                .doesNotContain("<p><div data-resizable-video");
        assertThat(html.indexOf("data-resizable-video")).isLessThan(html.indexOf("Depois."));
    }

    /**
     * Marcador devolvido como tag de vídeo ainda vira o player no lugar certo —
     * modelo nenhum é obrigado a acertar o formato para a gravação ficar junto
     * do seu assunto.
     */
    @Test void aVideoTagCarryingTheMarkerStillBecomesThePlayerInPlace() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "<h2>Emissão</h2><p>Como emitir.</p>"
                + "<video controls><source src=\"VIDEO-1\" type=\"video/mp4\"></video><p>Depois.</p>");

        String html = service.comIa(9L, "groq").html();

        assertThat(html)
                .doesNotContain("<video").doesNotContain("<source").doesNotContain("<h2>Demonstrações</h2>")
                .contains("data-src=\"https://player.vimeo.com/video/1226964093?h=12293c01c4\"");
        assertThat(html.indexOf("data-resizable-video")).isLessThan(html.indexOf("Depois."));
    }

    /**
     * Gravação que o modelo esqueceu de posicionar não se perde, e não vira
     * player anônimo: vai para o fim com o assunto ao lado.
     */
    @Test void aForgottenRecordingIsAppendedWithItsSubject() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Cancelar nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn("<h2>Cancelamento</h2><p>Texto.</p>");

        assertThat(service.comIa(9L, "groq").html())
                .contains("<h2>Demonstrações</h2>")
                .contains("Demonstração: Cancelar nota")
                .contains("data-resizable-video");
    }

    /** Resposta vazia do modelo ainda produz documento, com o que os casos dizem. */
    @Test void anEmptyAnswerFallsBackToTheCaseContent() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn("   ");

        assertThat(service.comIa(9L, "groq").html())
                .contains("<h2>Emitir nota</h2>")
                .contains("Esperado de Emitir nota")
                .contains("data-resizable-video");
    }

    // ── Documentação por IA: IA local ─────────────────────────────────────────

    /**
     * O modelo pequeno recebe uma pergunta por assunto, mais a da visão geral.
     * Pedir o documento inteiro a ele devolvia a planilha de execução da suíte.
     */
    @Test void theLocalModelIsAskedOneSubjectAtATime() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", GRAVACAO), caso(2L, 2L, "Cancelar nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn("<p>Explicação.</p>");

        String html = service.comIa(9L, "ollama").html();

        verify(provider, times(3)).texto(anyString(), anyInt());   // visão geral + 2 assuntos
        assertThat(html)
                .contains("<h2>Visão geral</h2>")
                .contains("<h2>Emitir nota</h2>")
                .contains("<h2>Cancelar nota</h2>");
    }

    /** Também aqui a gravação nasce embaixo do assunto que ela demonstra. */
    @Test void withTheLocalModelEveryRecordingSitsUnderItsSubject() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", GRAVACAO), caso(2L, 2L, "Cancelar nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn("<p>Explicação.</p>");

        String html = service.comIa(9L, "ollama").html();

        assertThat(html).contains("<p><em>Demonstração: Emitir nota</em></p>");
        assertThat(html.indexOf("<h2>Emitir nota</h2>")).isLessThan(html.indexOf("data-resizable-video"));
        assertThat(html.indexOf("data-resizable-video")).isLessThan(html.indexOf("<h2>Cancelar nota</h2>"));
        assertThat(html.split("data-resizable-video", -1).length - 1).isEqualTo(1);
    }

    /**
     * Um assunto que falha não derruba o documento: entra com o conteúdo do
     * próprio caso. Perder quinze assuntos porque o décimo deu erro seria o pior
     * dos resultados.
     */
    @Test void aFailedSubjectDoesNotBringTheDocumentDown() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", null), caso(2L, 2L, "Cancelar nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("<p>Geral.</p>")
                .thenThrow(new IllegalStateException("modelo fora do ar"))
                .thenReturn("<p>Como cancelar.</p>");

        String html = service.comIa(9L, "ollama").html();

        assertThat(html)
                .contains("<h2>Emitir nota</h2>")
                .contains("Esperado de Emitir nota")      // conteúdo do caso, no lugar do texto
                .contains("<p>Como cancelar.</p>")
                .contains("data-resizable-video");
    }

    // ── Documentação por IA: regras que valem nos dois caminhos ───────────────

    /**
     * Documentação é do produto, não do teste. Enquanto o modelo recebia código
     * de caso e status, devolvia "CT-1 / Em progresso" — que é justamente o que
     * não se publica para quem só quer aprender a usar o sistema.
     */
    @Test void noModelEverSeesCaseCodesStatusOrVideoAddresses() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn("<p>ok</p>");

        service.comIa(9L, "groq");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).texto(prompt.capture(), anyInt());
        assertThat(prompt.getValue()).doesNotContain(GRAVACAO).doesNotContain("player.vimeo.com");
        // As proibições citam "CT-1" na própria regra; o que importa é o material.
        String material = prompt.getValue().substring(prompt.getValue().indexOf("Material:"));
        assertThat(material)
                .contains("Emitir nota")
                .contains("Esperado de Emitir nota")
                .doesNotContain("CT-1")
                .doesNotContain("Aprovado")
                .doesNotContain("Status:");
    }

    /**
     * A regra no prompt sozinha não segura um modelo pequeno: o que chegou à
     * tela foi uma tabela de "Caso CT-1 / Observações / Status". A tabela é
     * desmontada — o conteúdo fica, a planilha some.
     */
    @Test void aTestTableInTheAnswerIsUnwound() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "<table><tr><th>Caso</th><th>Status</th></tr>"
                + "<tr><td>CT-1</td><td>Preencher o ICMS</td><td>Em progresso</td></tr></table>");

        String html = service.comIa(9L, "groq").html();

        assertThat(html)
                .doesNotContain("<table>").doesNotContain("<td>").doesNotContain("CT-1")
                .contains("Preencher o ICMS");
    }

    /**
     * Vídeo que o modelo escreveu sozinho não entra: nenhum provider recebe
     * endereço de gravação, então todo id que apareça na resposta saiu da cabeça
     * dele, e iframe de vídeo desconhecido não se publica.
     */
    @Test void aVideoTheModelInventedIsDropped() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "<p>Antes</p><div data-resizable-video=\"\" data-src=\"https://player.vimeo.com/video/999\"></div>"
                + "<p>[[VIDEO-1]]</p>");

        String html = service.comIa(9L, "groq").html();

        assertThat(html).doesNotContain("video/999");
        assertThat(html).contains("data-src=\"https://player.vimeo.com/video/1226964093?h=12293c01c4\"");
        assertThat(html.split("data-resizable-video", -1).length - 1).isEqualTo(1);
    }

    /** Endereço de gravação escrito no texto do caso vira player, e não link. */
    @Test void aRecordingUrlFromTheCaseTextBecomesAPlayer() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn("<p>Veja " + GRAVACAO + "</p><p>[[VIDEO-1]]</p>");

        assertThat(service.comIa(9L, "groq").html())
                .contains("data-src=\"https://player.vimeo.com/video/1226964093?h=12293c01c4\"");
    }

    /** Cerca de código, esqueleto de página e script não chegam ao editor. */
    @Test void fenceSkeletonAndScriptsAreStripped() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "```html\n<!DOCTYPE html><html><body><meta charset=\"UTF-8\"><title>Como usar</title>"
                + "<h2>Material</h2><script>alert(1)</script></body></html>\n```");

        assertThat(service.comIa(9L, "groq").html())
                .doesNotContain("<meta").doesNotContain("<title").doesNotContain("<script")
                .doesNotContain("<html").doesNotContain("```")
                .contains("<h2>Material</h2>");
    }

    /** Link que não é do Vimeo continua link. */
    @Test void aiOtherLinksAreUntouched() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("<p><a href=\"https://ultracar.com.br/ajuda\">Manual</a></p>");

        String html = service.comIa(9L, "groq").html();
        assertThat(html).contains("<a href=\"https://ultracar.com.br/ajuda\">Manual</a>");
        assertThat(html).doesNotContain("data-resizable-video");
    }

    @Test void aiDocRefusesAnEmptySuite() {
        suite.setCases(new HashSet<>());
        assertThatThrownBy(() -> service.comIa(9L, null)).hasMessageContaining("não tem casos de teste");
        verifyNoInteractions(provider);
    }

    // ── Gherkin ───────────────────────────────────────────────────────────────

    @Test void gherkinAsksForScenariosInOrderAndKeepsTheRecording() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", GRAVACAO), caso(2L, 2L, "Cancelar nota", null))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("Funcionalidade: Fiscal\n\n  Cenário: Emitir nota\n    Dado que...");

        SuiteDocService.Documento doc = service.gherkin(9L, "groq");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).texto(prompt.capture(), anyInt());
        assertThat(prompt.getValue())
                .contains("Gherkin")
                .contains("português do Brasil")
                .contains("Emitir nota")
                .contains("Cancelar nota")
                .contains(GRAVACAO);
        assertThat(doc.format()).isEqualTo("gherkin");
        // Texto puro para exportar .feature; no editor, dentro de bloco de código.
        assertThat(doc.content()).startsWith("Funcionalidade:");
        assertThat(doc.html()).startsWith("<pre><code>Funcionalidade:");
    }

    /**
     * Palavra-chave que voltar em inglês é traduzida aqui. A instrução de
     * idioma vai em toda chamada de IA, mas instrução é pedido: um modelo
     * pequeno devolveu a suíte inteira como "Feature / Scenario / Given".
     */
    @Test void englishKeywordsAreTranslated() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "Feature: Tax Computation\n"
                + "  Scenario: A person issues a credit note\n"
                + "    Given a person wants to issue a note\n"
                + "    When the system processes the request\n"
                + "    Then the request is blocked\n"
                + "    And the message is shown\n"
                + "    But nothing is saved\n"
                + "  Scenario Outline: Many finalities\n"
                + "    Examples:\n");

        String gherkin = service.gherkin(9L, null).content();

        assertThat(gherkin)
                .contains("Funcionalidade: Tax Computation")
                .contains("Cenário: A person issues a credit note")
                .contains("Dado a person wants")
                .contains("Quando the system")
                .contains("Então the request")
                .contains("E the message")
                .contains("Mas nothing")
                .contains("Esquema do Cenário: Many finalities")
                .contains("Exemplos:")
                .doesNotContain("Feature:").doesNotContain("Scenario")
                .doesNotContain("Given ").doesNotContain("When ").doesNotContain("Then ");
    }

    /** O recuo é preservado: em Gherkin a indentação é o que dá a leitura. */
    @Test void translationKeepsTheIndentation() {
        assertThat(SuiteDocService.emPortugues("  Scenario: Um\n    Given dois"))
                .isEqualTo("  Cenário: Um\n    Dado dois");
    }

    /** Palavra que só começa igual não é palavra-chave. */
    @Test void aWordThatMerelyStartsTheSameIsNotAKeyword() {
        assertThat(SuiteDocService.emPortugues("Andar de cima")).isEqualTo("Andar de cima");
        assertThat(SuiteDocService.emPortugues("Whenever possível")).isEqualTo("Whenever possível");
    }

    /**
     * O Gherkin descreve o comportamento do sistema, não o andamento do teste:
     * um passo "E o status é 'em progresso'" vinha de o código e o status do
     * caso irem para o modelo.
     */
    @Test void gherkinMaterialCarriesNoCaseCodeOrStatus() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", GRAVACAO))));
        when(provider.texto(anyString(), anyInt())).thenReturn("Funcionalidade: Fiscal");

        service.gherkin(9L, null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).texto(prompt.capture(), anyInt());
        String material = prompt.getValue().substring(prompt.getValue().indexOf("Material:"));
        assertThat(material)
                .contains("Emitir nota")
                .contains(GRAVACAO)          // a evidência continua indo
                .doesNotContain("CT-1")
                .doesNotContain("Aprovado")
                .doesNotContain("Status:");
    }

    @Test void gherkinStripsTheCodeFence() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("```gherkin\nFuncionalidade: Fiscal\n```");

        assertThat(service.gherkin(9L, null).content()).isEqualTo("Funcionalidade: Fiscal");
    }

    /**
     * O modelo de nuvem converte a suíte inteira numa chamada; a IA local, um
     * cenário por vez. Pedir a suíte toda ao modelo pequeno devolvia um cenário
     * só para cinco casos, e sem a linha de abertura.
     */
    @Test void theLocalModelConvertsOneScenarioAtATime() {
        suite.setCases(new HashSet<>(List.of(
                caso(1L, 1L, "Emitir nota", GRAVACAO), caso(2L, 2L, "Cancelar nota", null))));
        when(provider.texto(anyString(), anyInt()))
                .thenReturn("Cenário: Um caso\nDado algo\nQuando age\nEntão responde");

        String gherkin = service.gherkin(9L, "ollama").content();

        verify(provider, times(2)).texto(anyString(), anyInt());   // um por caso
        assertThat(gherkin)
                .startsWith("Funcionalidade: Fiscal")
                .contains("  Cenário: Um caso")
                .contains("    Dado algo")
                .contains("    # Evidência: " + GRAVACAO);
        assertThat(gherkin.split("Cenário:", -1).length - 1).isEqualTo(2);
    }

    /**
     * Pede-se um cenário e o modelo pequeno devolve um e meio, com Markdown no
     * meio e restos de cerca de código. No editor isso fica à vista de quem for
     * ler, então é saneado antes de sair.
     */
    @Test void theLocalModelsNoiseIsCleanedOffTheScenario() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "Aqui está o cenário:\n"
                + "Cenário: Nota sem ICMS\n"
                + "Dado: uma nota de crédito\n"
                + "Quando: a nota é emitida\n"
                + "Então: o sistema aceita\n"
                + "o JSON não traz a chave icms\n"
                + "sql\n"
                + "**Cenário: Outro que ninguém pediu**\n"
                + "**Dado:** N/A\n");

        String gherkin = service.gherkin(9L, "ollama").content();

        assertThat(gherkin)
                .contains("  Cenário: Nota sem ICMS")
                .contains("    Dado uma nota de crédito")      // sem o dois-pontos
                .contains("    Quando a nota é emitida")
                .contains("    E o JSON não traz a chave icms")  // continuação vira passo
                .doesNotContain("**")
                .doesNotContain("Aqui está")
                .doesNotContain("Outro que ninguém pediu")
                .doesNotContain("N/A");
        assertThat(gherkin.split("Cenário:", -1).length - 1).isEqualTo(1);
        // "sql" sozinho é resto de cerca de código, não um passo.
        assertThat(gherkin).doesNotContain("sql");
    }

    /**
     * O modelo pequeno escreve em formato de rascunho: a palavra-chave numa
     * linha e o texto dela na seguinte, como item de lista, e o título vazio.
     * Isso é remontado aqui — no editor ficaria à vista de quem for ler.
     */
    @Test void aDraftShapedScenarioIsPutBackIntoGherkin() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn(
                "Cenário\n"
                + "Dado\n"
                + "- o usuário abre a nota\n"
                + "Quando\n"
                + "- salva sem o ICMS\n"
                + "Então\n"
                + "- o sistema bloqueia\n");

        String gherkin = service.gherkin(9L, "ollama").content();

        assertThat(gherkin)
                .contains("  Cenário: Emitir nota")      // título vazio recebe o do caso
                .contains("    Dado o usuário abre a nota")
                .contains("    Quando salva sem o ICMS")
                .contains("    Então o sistema bloqueia")
                .doesNotContain("- o usuário");
    }

    /** Título sem passo nenhum não é cenário: entra o de reserva. */
    @Test void aTitleWithoutStepsFallsBackToTheCase() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn("Cenário: Só o título");

        assertThat(service.gherkin(9L, "ollama").content())
                .contains("Cenário: Emitir nota")
                .contains("Então Esperado de Emitir nota")
                .doesNotContain("Só o título");
    }

    /** Caso que o modelo não converteu vira cenário com o que o próprio caso diz. */
    @Test void aCaseTheLocalModelSkippedBecomesAScenarioAnyway() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn("Não consegui converter.");

        assertThat(service.gherkin(9L, "ollama").content())
                .contains("Cenário: Emitir nota")
                .contains("Dado que Emitir nota")
                .contains("Então Esperado de Emitir nota")
                .doesNotContain("Não consegui");
    }

    /** A linha de abertura é obrigatória, e é a que mais se perde na resposta. */
    @Test void theFeatureLineIsAddedWhenTheModelForgetsIt() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota", null))));
        when(provider.texto(anyString(), anyInt())).thenReturn("  Cenário: Emissão\n    Dado algo");

        assertThat(service.gherkin(9L, "groq").content())
                .startsWith("Funcionalidade: Fiscal")
                .contains("Cenário: Emissão");
    }

    @Test void gherkinRefusesAnEmptySuite() {
        suite.setCases(new HashSet<>());
        assertThatThrownBy(() -> service.gherkin(9L, null)).hasMessageContaining("não tem casos de teste");
    }

    @Test void refusesAnUnknownSuite() {
        assertThatThrownBy(() -> service.exato(99L)).hasMessageContaining("não encontrada");
    }
}
