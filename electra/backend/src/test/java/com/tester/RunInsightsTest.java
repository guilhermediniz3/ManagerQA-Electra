package com.tester;

import com.tester.runmanager.FailureClassifier;
import com.tester.runmanager.PlaywrightReportReader;
import com.tester.runmanager.RunCatalogDTOs.EnvironmentDTO;
import com.tester.runmanager.RunCatalogDTOs.SystemDTO;
import com.tester.runmanager.RunCatalogService;
import com.tester.runmanager.RunInsightsService;
import com.tester.runmanager.RunPanelDTOs.CardDTO;
import com.tester.runmanager.RunPanelDTOs.ContainerDTO;
import com.tester.runmanager.RunPanelService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Falhas lidas do relatório do Playwright, e as métricas que saem delas.
 *
 * O teste escreve um `results.json` de verdade na pasta do projeto, porque o
 * que se quer garantir é o reconhecimento daquele arquivo — ele é a única fonte
 * que diz qual caso falhou e onde está o vídeo.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:run_insights;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RunInsightsTest {

    @Autowired RunPanelService painel;
    @Autowired RunCatalogService catalogo;
    @Autowired RunInsightsService insights;
    @Autowired PlaywrightReportReader leitor;

    static Path raiz;

    @BeforeAll
    static void montarProjeto() throws Exception {
        raiz = Files.createTempDirectory("projeto-com-falha");
        Files.createDirectories(raiz.resolve("test-results"));
        Files.writeString(raiz.resolve("test-results/results.json"), """
            {
              "suites": [
                {
                  "title": "tests/v2/workorder/ordem.spec.ts",
                  "file": "tests/v2/workorder/ordem.spec.ts",
                  "suites": [
                    {
                      "title": "Ordem de serviço",
                      "specs": [
                        {
                          "title": "CT-014: fecha a ordem",
                          "ok": false,
                          "file": "tests/v2/workorder/ordem.spec.ts",
                          "tests": [
                            {
                              "results": [
                                {
                                  "status": "failed",
                                  "error": { "message": "[REGRA_NEGOCIO] O total ficou 120, esperado 100" },
                                  "attachments": [
                                    { "name": "video", "path": "test-results/ordem/video.webm" },
                                    { "name": "screenshot", "path": "test-results/ordem/falha.png" }
                                  ]
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "title": "passa sempre",
                          "ok": true,
                          "tests": []
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """);
    }

    @Test
    void leOCasoQueFalhouComVideoEScreenshot() {
        var achadas = leitor.ler(raiz);
        assertThat(achadas).hasSize(1);
        var falha = achadas.get(0);
        assertThat(falha.title()).isEqualTo("CT-014: fecha a ordem");
        assertThat(falha.module()).isEqualTo("workorder");
        assertThat(falha.version()).isEqualTo("v2");
        assertThat(falha.videoPath()).endsWith("video.webm");
        assertThat(falha.screenshotPath()).endsWith("falha.png");
        assertThat(falha.fullName()).contains("Ordem de serviço");
    }

    @Test
    void projetoSemRelatorioNaoEErro() throws Exception {
        assertThat(leitor.ler(Files.createTempDirectory("sem-relatorio"))).isEmpty();
    }

    /**
     * Falha de produto e falha de automação pedem ações opostas — uma vira
     * demanda, a outra vira conserto no teste. Confundi-las faz o painel apontar
     * defeito onde não há.
     */
    @Test
    void separaFalhaDeProdutoDeFalhaDeAutomacao() {
        assertThat(FailureClassifier.classificar("[REGRA_NEGOCIO] total errado").bucket())
                .isEqualTo("REGRA_NEGOCIO");
        assertThat(FailureClassifier.classificar("net::ERR_CONNECTION_REFUSED at login").bucket())
                .isEqualTo("INFRA");
        assertThat(FailureClassifier.classificar("locator.click: Timeout 30000ms exceeded").bucket())
                .isEqualTo("LOCATOR");
        assertThat(FailureClassifier.classificar("API login failed: 500").bucket())
                .isEqualTo("STATUS_CODE");
        assertThat(FailureClassifier.classificar("algo inesperado").bucket()).isEqualTo("OUTRO");
    }

    /** A marca de regra de negócio vence a de locator: é decisão, não empate. */
    @Test
    void regraDeNegocioVenceLocator() {
        assertThat(FailureClassifier.classificar(
                "[REGRA_NEGOCIO] valor errado\n  waiting for locator('#total')").bucket())
                .isEqualTo("REGRA_NEGOCIO");
    }

    @Test
    void extraiOCodigoSoQuandoEleExiste() {
        assertThat(FailureClassifier.classificar("API login failed: 401").statusCode()).isEqualTo(401);
        assertThat(FailureClassifier.classificar("Timeout 45000ms exceeded").statusCode()).isNull();
    }

    @Test
    void guardaAsFalhasDaExecucaoEAsSomaNasMetricas() throws Exception {
        var sistema = catalogo.salvarSistema(new SystemDTO(null, "Com falha " + System.nanoTime(),
                null, null, null, raiz.toString(), null, null, null, true, 0, List.of()));
        var ambiente = catalogo.salvarAmbiente(new EnvironmentDTO(null, "HML " + System.nanoTime(), null, false, null, true, 0));
        var container = painel.salvarContainer(new ContainerDTO(null, "Suíte", null, true, 0, List.of()));
        var card = painel.salvarCard(new CardDTO(null, container.id(), "HML", ambiente.id(), null,
                false, sistema.id(), null, null, null, null, null, "echo terminou", true, 0));

        var execucao = painel.dispararCard(card.id(), "Diniz");
        for (int i = 0; i < 100 && "RUNNING".equals(painel.execucao(execucao.id()).state()); i++)
            Thread.sleep(200);

        var registradas = insights.falhas(execucao.id());
        assertThat(registradas).hasSize(1);
        assertThat(registradas.get(0).bucket()).isEqualTo("REGRA_NEGOCIO");
        assertThat(registradas.get(0).module()).isEqualTo("workorder");
        assertThat(registradas.get(0).vimeoUrl()).isNull();
        assertThat(registradas.get(0).jiraKey()).isNull();

        var metricas = insights.metricas(null, null);
        assertThat(metricas.resumo().total()).isPositive();
        assertThat(metricas.modules()).extracting(RunInsightsService.PorModulo::module)
                .contains("workorder");
        assertThat(metricas.buckets()).extracting(RunInsightsService.PorBucket::bucket)
                .contains("REGRA_NEGOCIO");
    }

    @Test
    void aTaxaDeSucessoOlhaSoOQueTerminou() {
        var metricas = insights.metricas(null, null);
        assertThat(metricas.resumo().taxaDeSucesso()).isBetween(0.0, 100.0);
    }
}
