package com.tester;

import com.tester.runmanager.ProjectCatalogService;
import com.tester.runmanager.ProjectCatalogService.ManualDTO;
import com.tester.runmanager.ProjectScanner;
import com.tester.runmanager.RunCatalogDTOs;
import com.tester.runmanager.RunCatalogDTOs.EnvironmentDTO;
import com.tester.runmanager.RunCatalogDTOs.SystemDTO;
import com.tester.runmanager.RunCatalogService;
import com.tester.runmanager.RunSelectionService;
import com.tester.runmanager.RunSelectionService.SelecaoDTO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Leitura do projeto de automação e execução por seleção.
 *
 * O teste monta um projeto Playwright de mentira no disco — as pastas e o
 * formato que a leitura reconhece — porque é exatamente esse reconhecimento que
 * está sendo verificado.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:run_project;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RunProjectTest {

    @Autowired ProjectCatalogService projeto;
    @Autowired RunCatalogService catalogo;
    @Autowired RunSelectionService selecao;

    static Path raiz;

    @BeforeAll
    static void montarProjetoDeMentira() throws Exception {
        raiz = Files.createTempDirectory("projeto-playwright");
        Path auth = raiz.resolve("tests/api/auth");
        Files.createDirectories(auth);
        Files.writeString(auth.resolve("auth.api.spec.ts"), """
            import { test, expect } from '@playwright/test';

            test.describe('Autenticação @api @smoke', () => {
              test('CT-001: should obtain an access token with valid credentials', async () => {
              });

              test('should reject invalid credentials @regression', async () => {
              });
            });
            """);

        Path dashboard = raiz.resolve("tests/v2/directSale");
        Files.createDirectories(dashboard);
        Files.writeString(dashboard.resolve("venda.spec.ts"), """
            test.describe('Venda direta @journey', () => {
              test('CT-010: conclui uma venda', async () => {});
            });
            """);

        // Pastas que não são módulo de ninguém e não podem virar linha na tela.
        Files.createDirectories(raiz.resolve("tests/setup"));
        Files.writeString(raiz.resolve("tests/setup/global.spec.ts"),
                "test('prepara ambiente', async () => {});");
        Files.createDirectories(raiz.resolve("node_modules/pacote"));
        // O config precisa ter projects de verdade: é de onde saem as versões,
        // e é o que o comando escolhe com --project.
        Files.writeString(raiz.resolve("playwright.config.ts"), """
            import { defineConfig } from '@playwright/test';
            export default defineConfig({
              projects: [
                { name: 'unit', testDir: './tests/unit' },
                { name: 'api', testDir: './tests/api' },
                { name: 'setup-v2', testDir: './tests/v2/setup' },
                {
                  name: 'v2',
                  testDir: './tests/v2',
                  dependencies: ['setup-v2'],
                },
                { name: 'v3', testDir: './tests/v3' },
              ],
            });
            """);
    }

    private SystemDTO sistemaCom(String selectionScript) {
        return catalogo.salvarSistema(new SystemDTO(null, "Sistema " + System.nanoTime(), null,
                null, null, raiz.toString(), selectionScript, null, null, true, 0, List.of()));
    }

    @Test
    void leOsModulosEOsCasosDoProjeto() {
        var leitura = projeto.reler(sistemaCom(null).id(), "Diniz");

        assertThat(leitura.modules()).extracting(ProjectCatalogService.ModuloDTO::key)
                .containsExactlyInAnyOrder("api/auth", "v2/directSale");
        assertThat(leitura.totalTests()).isEqualTo(3);
        assertThat(leitura.scannedBy()).isEqualTo("Diniz");

        var auth = leitura.modules().stream().filter(m -> m.key().equals("api/auth")).findFirst().orElseThrow();
        assertThat(auth.name()).isEqualTo("Auth");
        assertThat(auth.tests()).extracting(ProjectCatalogService.CasoDTO::code)
                .containsExactly("CT-001", null);
        assertThat(auth.tests().get(0).title()).isEqualTo("should obtain an access token with valid credentials");
        // A tag do describe vale para os casos dentro dele, somada às do caso.
        assertThat(auth.tests().get(0).tags()).contains("api", "smoke");
        assertThat(auth.tests().get(1).tags()).contains("regression");
    }

    /** `directSale` precisa aparecer legível, senão a lista vira nome de pasta. */
    @Test
    void nomeDeModuloFicaLegivel() {
        assertThat(ProjectScanner.humanizar("directSale")).isEqualTo("Direct Sale");
        assertThat(ProjectScanner.humanizar("test-plans")).isEqualTo("Test plans");
    }

    @Test
    void ignoraSetupENodeModules() {
        var leitura = projeto.reler(sistemaCom(null).id(), "Diniz");
        assertThat(leitura.modules()).extracting(ProjectCatalogService.ModuloDTO::key)
                .doesNotContain("setup", "pacote");
    }

    @Test
    void semCaminhoLocalNaoHaOQueLer() {
        var sistema = catalogo.salvarSistema(new SystemDTO(null, "Sem caminho " + System.nanoTime(),
                null, null, null, null, null, null, null, true, 0, List.of()));
        assertThatThrownBy(() -> projeto.reler(sistema.id(), "Diniz"))
                .hasMessageContaining("caminho local");
    }

    /** Caso cadastrado à mão aparece junto dos lidos, no mesmo módulo. */
    @Test
    void casoManualEntraNoMesmoModulo() {
        var sistema = sistemaCom(null);
        projeto.reler(sistema.id(), "Diniz");
        projeto.salvarManual(new ManualDTO(null, sistema.id(), "api/auth", "Auth", "api", "CT-900",
                "Conferir expiração do token", "npm run token:check", "manual", true));

        var auth = projeto.casos(sistema.id()).modules().stream()
                .filter(m -> m.key().equals("api/auth")).findFirst().orElseThrow();
        assertThat(auth.tests()).extracting(ProjectCatalogService.CasoDTO::title)
                .contains("Conferir expiração do token");
        assertThat(auth.tests()).filteredOn(ProjectCatalogService.CasoDTO::manual).hasSize(1);
    }

    @Test
    void casoManualDeModuloDesconhecidoNaoSome() {
        var sistema = sistemaCom(null);
        projeto.salvarManual(new ManualDTO(null, sistema.id(), "v9/novo", "Módulo novo", "v9", null,
                "Verificação manual", "echo ok", null, true));
        assertThat(projeto.casos(sistema.id()).modules()).extracting(ProjectCatalogService.ModuloDTO::key)
                .contains("v9/novo");
    }

    // ------------------------------------------------------- execução por seleção

    @Test
    void montaOComandoDaSelecaoComOsAlvosMarcados() {
        String comando = RunSelectionService.montar(null,
                List.of("tests/api/auth/auth.api.spec.ts:4"), List.of());
        assertThat(comando).isEqualTo("npx playwright test tests/api/auth/auth.api.spec.ts:4");
    }

    /** Quem usa outra ferramenta escreve o próprio comando, sem versão nova. */
    @Test
    void respeitaOComandoCadastradoNoSistema() {
        String comando = RunSelectionService.montar("npx cypress run --spec {{casos}}",
                List.of("cypress/e2e/login.cy.ts"), List.of());
        assertThat(comando).isEqualTo("npx cypress run --spec cypress/e2e/login.cy.ts");
    }

    @Test
    void casoManualEntraComoComandoEncadeado() {
        String comando = RunSelectionService.montar(null, List.of("tests/a.spec.ts:1"),
                List.of("npm run token:check"));
        assertThat(comando).isEqualTo("npx playwright test tests/a.spec.ts:1 && npm run token:check");
    }

    /** Caminho com espaço quebraria o comando ao meio sem as aspas. */
    @Test
    void alvoComEspacoVaiEntreAspas() {
        assertThat(RunSelectionService.montar(null, List.of("tests/minha pasta/a.spec.ts:1"), List.of()))
                .contains("\"tests/minha pasta/a.spec.ts:1\"");
    }

    @Test
    void selecaoVaziaNaoDispara() {
        var sistema = sistemaCom(null);
        assertThatThrownBy(() -> selecao.disparar(new SelecaoDTO(sistema.id(), null, null, List.of()), "Diniz"))
                .hasMessageContaining("ao menos um caso");
    }

    @Test
    void selecaoDeCasosReaisVeraDisparo() throws Exception {
        var sistema = sistemaCom("echo {{casos}}");
        var ambiente = catalogo.salvarAmbiente(new EnvironmentDTO(null, "HML " + System.nanoTime(), null, false, null, true, 0));
        var leitura = projeto.reler(sistema.id(), "Diniz");
        var primeiro = leitura.modules().get(0).tests().get(0);

        var execucao = selecao.disparar(new SelecaoDTO(sistema.id(), ambiente.id(),
                "Seleção do teste", List.of(primeiro.id())), "Diniz");

        assertThat(execucao.title()).isEqualTo("Seleção do teste");
        assertThat(execucao.script()).contains(primeiro.file());
    }

    // ------------------------------------------------------------- versões

    /**
     * Versão, no Playwright, é "project": está declarada no config, com a
     * própria pasta. Copiá-las à mão para a tela seria manter uma segunda
     * lista da mesma coisa — que muda sem avisar ninguém.
     */
    @Test
    void leAsVersoesDoArquivoDeConfiguracao() {
        var alvos = ProjectScanner.alvos(raiz);
        assertThat(alvos).extracting(ProjectScanner.Alvo::name)
                .containsExactly("unit", "api", "setup-v2", "v2", "v3");
        assertThat(alvos.get(0).testDir()).isEqualTo("./tests/unit");
    }

    /** Quem é dependência de outro roda junto e não vira botão de disparo. */
    @Test
    void reconheceOsProjetosAuxiliares() {
        var alvos = ProjectScanner.alvos(raiz);
        assertThat(alvos).filteredOn(ProjectScanner.Alvo::auxiliar)
                .extracting(ProjectScanner.Alvo::name).containsExactly("setup-v2");
    }

    @Test
    void aReleituraCadastraAsVersoesEncontradas() {
        var sistema = sistemaCom(null);
        projeto.reler(sistema.id(), "Diniz");

        var versoes = catalogo.listarSistemas().stream()
                .filter(x -> x.id().equals(sistema.id())).findFirst().orElseThrow().versions();
        assertThat(versoes).extracting(RunCatalogDTOs.VersionDTO::name)
                .contains("v2", "v3", "unit", "api");
        // O auxiliar entra inativo: aparece no cadastro, não no disparo.
        assertThat(versoes).filteredOn(v -> v.name().equals("setup-v2"))
                .allMatch(v -> !v.active());
    }

    /** Reler de novo não duplica nem desfaz o que alguém ajustou. */
    @Test
    void relerDuasVezesNaoDuplicaVersao() {
        var sistema = sistemaCom(null);
        projeto.reler(sistema.id(), "Diniz");
        projeto.reler(sistema.id(), "Diniz");

        var versoes = catalogo.listarSistemas().stream()
                .filter(x -> x.id().equals(sistema.id())).findFirst().orElseThrow().versions();
        assertThat(versoes).filteredOn(v -> v.name().equals("v2")).hasSize(1);
    }

    /**
     * Versão nova no projeto aparece na próxima leitura, sem cadastro à mão.
     *
     * É o ponto de ler do arquivo de configuração em vez de manter uma lista
     * paralela: quando o time criar a v4, ela chega sozinha.
     */
    @Test
    void versaoNovaNoProjetoApareceNaProximaLeitura() throws Exception {
        var sistema = sistemaCom(null);
        projeto.reler(sistema.id(), "Diniz");

        String config = Files.readString(raiz.resolve("playwright.config.ts"));
        Files.writeString(raiz.resolve("playwright.config.ts"),
                config.replace("{ name: 'v3', testDir: './tests/v3' },",
                        "{ name: 'v3', testDir: './tests/v3' },"
                        + System.lineSeparator() + "    { name: 'v4', testDir: './tests/v4' },"));
        try {
            projeto.reler(sistema.id(), "Diniz");
            var versoes = catalogo.listarSistemas().stream()
                    .filter(x -> x.id().equals(sistema.id())).findFirst().orElseThrow().versions();
            assertThat(versoes).extracting(RunCatalogDTOs.VersionDTO::name).contains("v4");
        } finally {
            Files.writeString(raiz.resolve("playwright.config.ts"), config);
        }
    }

    /** Projeto sem arquivo de configuração não quebra a leitura dos casos. */
    @Test
    void projetoSemConfigDePlaywrightNaoQuebra() throws Exception {
        Path outro = Files.createTempDirectory("projeto-sem-config");
        Files.createDirectories(outro.resolve("tests/v1/login"));
        Files.writeString(outro.resolve("tests/v1/login/login.spec.ts"),
                "test('CT-001: entra no sistema', async () => {});");

        assertThat(ProjectScanner.alvos(outro)).isEmpty();
        var sistema = catalogo.salvarSistema(new SystemDTO(null, "Outro " + System.nanoTime(),
                null, null, null, outro.toString(), null, null, null, true, 0, List.of()));
        var leitura = projeto.reler(sistema.id(), "Diniz");
        assertThat(leitura.totalTests()).isEqualTo(1);
    }
}
