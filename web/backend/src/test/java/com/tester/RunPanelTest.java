package com.tester;

import com.tester.runmanager.RunCatalogDTOs.*;
import com.tester.runmanager.RunCatalogService;
import com.tester.runmanager.RunPanelDTOs.*;
import com.tester.runmanager.RunExecutionRecovery;
import com.tester.runmanager.RunPanelService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Painel de disparo: containers, cards e o que acontece ao clicar.
 *
 * O teste executa comando de verdade — um `echo` numa pasta temporária — porque
 * é o caminho inteiro que importa aqui: o card vira execução, a execução vira
 * processo, e o que o processo escreve tem que chegar ao terminal da tela.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:run_panel;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RunPanelTest {

    @Autowired RunPanelService painel;
    @Autowired RunCatalogService catalogo;
    @Autowired RunExecutionRecovery recuperacao;
    @Autowired com.tester.runmanager.repository.RunExecutionRepository execucoes;

    static Path pasta;
    SystemDTO sistema;
    EnvironmentDTO hml;

    @BeforeAll
    static void criarPasta() throws Exception {
        pasta = Files.createTempDirectory("runmanager-teste");
    }

    @BeforeEach
    void cadastrarBase() {
        sistema = catalogo.salvarSistema(new SystemDTO(null, "Sistema " + System.nanoTime(), null,
                null, null, pasta.toString(), null, null, null, true, 0, List.of()));
        hml = catalogo.salvarAmbiente(new EnvironmentDTO(null, "HML " + System.nanoTime(), null,
                false, null, true, 0));
    }

    private CardDTO cardCom(String script) {
        var container = painel.salvarContainer(new ContainerDTO(null, "Smoke V2",
                "Fumaça — o essencial de cada módulo", true, 0, List.of()));
        return painel.salvarCard(new CardDTO(null, container.id(), "HML", hml.id(), null, false,
                sistema.id(), null, null, null, null, null, script, true, 0));
    }

    private ExecutionDTO esperarFim(Long id) throws Exception {
        for (int i = 0; i < 100; i++) {
            var atual = painel.execucao(id);
            if (!"RUNNING".equals(atual.state())) return atual;
            Thread.sleep(200);
        }
        throw new AssertionError("A execução não terminou a tempo.");
    }

    @Test
    void containerGuardaOsCardsNaOrdem() {
        var container = painel.salvarContainer(new ContainerDTO(null, "Regressão", null, true, 0, List.of()));
        painel.salvarCard(new CardDTO(null, container.id(), "PROD", hml.id(), null, false,
                sistema.id(), null, null, null, null, null, "echo prod", true, 1));
        painel.salvarCard(new CardDTO(null, container.id(), "HML", hml.id(), null, false,
                sistema.id(), null, null, null, null, null, "echo hml", true, 0));

        var salvo = painel.listar(false).stream()
                .filter(c -> c.id().equals(container.id())).findFirst().orElseThrow();
        assertThat(salvo.cards()).extracting(CardDTO::title).containsExactly("HML", "PROD");
    }

    @Test
    void cardExigeTituloEScript() {
        var container = painel.salvarContainer(new ContainerDTO(null, "Vazio", null, true, 0, List.of()));
        assertThatThrownBy(() -> painel.salvarCard(new CardDTO(null, container.id(), " ", null, null,
                false, null, null, null, null, null, null, "echo", true, 0)))
                .hasMessageContaining("título");
        assertThatThrownBy(() -> painel.salvarCard(new CardDTO(null, container.id(), "HML", null, null,
                false, null, null, null, null, null, null, " ", true, 0)))
                .hasMessageContaining("script");
    }

    /** Inativar tira da tela de disparo sem apagar: o histórico continua apontando para ele. */
    @Test
    void cardInativoSaiDaTelaDeDisparoMasContinuaNoCadastro() {
        var card = cardCom("echo oi");
        painel.salvarCard(new CardDTO(card.id(), card.containerId(), card.title(), card.environmentId(),
                null, false, card.systemId(), null, null, null, null, null, card.script(), false, 0));

        var naTelaDeDisparo = painel.listar(true).stream()
                .filter(c -> c.id().equals(card.containerId())).findFirst();
        assertThat(naTelaDeDisparo).isPresent();
        assertThat(naTelaDeDisparo.get().cards()).isEmpty();

        var noCadastro = painel.listar(false).stream()
                .filter(c -> c.id().equals(card.containerId())).findFirst().orElseThrow();
        assertThat(noCadastro.cards()).extracting(CardDTO::id).contains(card.id());
    }

    @Test
    void cardInativoNaoDispara() {
        var card = cardCom("echo oi");
        painel.salvarCard(new CardDTO(card.id(), card.containerId(), card.title(), card.environmentId(),
                null, false, card.systemId(), null, null, null, null, null, card.script(), false, 0));
        assertThatThrownBy(() -> painel.dispararCard(card.id(), "Guilherme"))
                .hasMessageContaining("inativo");
    }

    @Test
    void executaOScriptEGuardaOQueSaiuNoTerminal() throws Exception {
        var card = cardCom("echo rodou-o-teste");
        var execucao = painel.dispararCard(card.id(), "Guilherme");
        assertThat(execucao.state()).isEqualTo("RUNNING");
        assertThat(execucao.engine()).isEqualTo("local");

        var fim = esperarFim(execucao.id());
        assertThat(fim.state()).isEqualTo("SUCCESS");
        assertThat(fim.exitCode()).isZero();
        assertThat(painel.log(execucao.id(), 0).chunk()).contains("rodou-o-teste");
        assertThat(fim.triggeredBy()).isEqualTo("Guilherme");
        assertThat(fim.durationSeconds()).isNotNull();
    }

    /** Código de saída diferente de zero é falha — é assim que o Playwright avisa. */
    @Test
    void scriptQueFalhaMarcaExecucaoComoFalha() throws Exception {
        var card = cardCom("exit 3");
        var fim = esperarFim(painel.dispararCard(card.id(), "Guilherme").id());
        assertThat(fim.state()).isEqualTo("FAILED");
        assertThat(fim.exitCode()).isEqualTo(3);
    }

    /**
     * O terminal lê a partir de onde parou. Sem isso, um log de megabytes
     * voltaria inteiro a cada segundo de execução.
     */
    @Test
    void terminalDevolveSoOTrechoNovo() throws Exception {
        var card = cardCom("echo primeira-linha");
        var execucao = painel.dispararCard(card.id(), "Guilherme");
        esperarFim(execucao.id());

        var tudo = painel.log(execucao.id(), 0);
        assertThat(tudo.chunk()).contains("primeira-linha");

        var depois = painel.log(execucao.id(), tudo.offset());
        assertThat(depois.chunk()).isEmpty();
        assertThat(depois.running()).isFalse();
    }

    /**
     * O log guarda o comando e o ambiente em que ele rodou.
     *
     * Sem as variáveis no cabeçalho, duas execuções do mesmo card em ambientes
     * diferentes produzem logs idênticos no começo — e depois ninguém consegue
     * dizer qual delas rodou em produção.
     */
    @Test
    void oLogGuardaOComandoEOAmbiente() throws Exception {
        var card = cardCom("echo x");
        var execucao = painel.dispararCard(card.id(), "Guilherme");
        esperarFim(execucao.id());

        var log = painel.log(execucao.id(), 0).chunk();
        assertThat(log).contains("ENV=" + hml.key());
        assertThat(log).contains("$ echo x");
    }

    @Test
    void semCaminhoNemRepositorioNaoHaComoExecutar() {
        var semLugar = catalogo.salvarSistema(new SystemDTO(null, "Sem lugar " + System.nanoTime(),
                null, null, null, null, null, null, null, true, 0, List.of()));
        var container = painel.salvarContainer(new ContainerDTO(null, "Solto", null, true, 0, List.of()));
        var card = painel.salvarCard(new CardDTO(null, container.id(), "HML", hml.id(), null, false,
                semLugar.id(), null, null, null, null, null, "echo oi", true, 0));

        assertThatThrownBy(() -> painel.dispararCard(card.id(), "Guilherme"))
                .hasMessageContaining("Nenhum motor de execução");
    }

    @Test
    void oHistoricoTrazAsExecucoes() throws Exception {
        var card = cardCom("echo historico");
        var execucao = painel.dispararCard(card.id(), "Guilherme");
        esperarFim(execucao.id());
        assertThat(painel.historico()).extracting(ExecutionDTO::id).contains(execucao.id());
    }

    /**
     * Depois que rodou, apagar levaria junto o registro do que aconteceu.
     * A mensagem precisa dizer isso e apontar a saída, senão quem tenta
     * excluir só vê um erro sem explicação.
     */
    @Test
    void cardComHistoricoNaoEApagadoPorEngano() throws Exception {
        var card = cardCom("echo historico");
        esperarFim(painel.dispararCard(card.id(), "Guilherme").id());

        assertThatThrownBy(() -> painel.excluirCard(card.id()))
                .hasMessageContaining("já foi executado")
                .hasMessageContaining("inativar");
        assertThatThrownBy(() -> painel.excluirContainer(card.containerId()))
                .hasMessageContaining("inativar");
    }

    @Test
    void cardQueNuncaRodouPodeSerApagado() {
        var card = cardCom("echo nunca");
        painel.excluirCard(card.id());
        var container = painel.listar(false).stream()
                .filter(c -> c.id().equals(card.containerId())).findFirst().orElseThrow();
        assertThat(container.cards()).isEmpty();
    }

    /**
     * O processo do teste vive dentro do aplicativo. Fechar a janela no meio
     * mata o processo, mas a linha no banco continuaria dizendo "em execução"
     * para sempre — e o painel mostraria um disparo que não existe.
     */
    @Test
    void execucaoInterrompidaPeloFechamentoNaoFicaRodandoParaSempre() {
        var presa = new com.tester.runmanager.entity.RunExecution();
        presa.setTitle("Ficou aberta");
        presa.setScript("echo x");
        presa.setState("RUNNING");
        presa.setStartedAt(java.time.LocalDateTime.now().minusHours(3));
        Long id = execucoes.save(presa).getId();

        recuperacao.fecharOQueFicouEmAberto();

        var depois = painel.execucao(id);
        assertThat(depois.state()).isEqualTo("CANCELLED");
        assertThat(depois.finishedAt()).isNotNull();
        assertThat(painel.log(id, 0).chunk()).contains("aplicativo foi encerrado");
    }

    /**
     * Interromper preserva o que já saiu e marca como interrompida.
     *
     * O fim do processo chega igual para quem foi morto e para quem falhou
     * sozinho. Sem cuidado, a gravação do fim vinha depois e sobrescrevia: o
     * estado virava falha e o log ia para o vazio.
     */
    @Test
    void interromperGuardaOLogEMarcaComoCancelada() throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        var card = cardCom(windows ? "ping -n 30 127.0.0.1" : "sleep 30");
        var execucao = painel.dispararCard(card.id(), "Guilherme");

        // Espera aparecer saída, senão o teste interromperia antes de existir log.
        for (int i = 0; i < 50 && painel.log(execucao.id(), 0).chunk().length() < 10; i++)
            Thread.sleep(200);
        assertThat(painel.log(execucao.id(), 0).chunk()).isNotEmpty();

        painel.cancelar(execucao.id());
        var fim = esperarFim(execucao.id());

        assertThat(fim.state()).isEqualTo("CANCELLED");
        var log = painel.log(execucao.id(), 0).chunk();
        assertThat(log).contains("$ ");
        assertThat(log).contains("interrompida por quem disparou");
    }

    /** Cancelar o que já terminou não pode reescrever o resultado. */
    @Test
    void cancelarDepoisDeTerminarNaoMudaNada() throws Exception {
        var card = cardCom("echo pronto");
        var execucao = painel.dispararCard(card.id(), "Guilherme");
        esperarFim(execucao.id());

        painel.cancelar(execucao.id());
        assertThat(painel.execucao(execucao.id()).state()).isEqualTo("SUCCESS");
    }

    /**
     * O ambiente precisa chegar ao processo, não só ao rótulo do botão.
     *
     * O projeto de automação escolhe a URL pelo que encontra no ambiente do
     * processo; um card de produção que não passe nada rodaria em homologação
     * com o botão vermelho.
     */
    @Test
    void oAmbienteChegaNoProcessoQueRoda() throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        var prod = catalogo.salvarAmbiente(new EnvironmentDTO(null, "PROD " + System.nanoTime(),
                null, true, null, true, 0));

        var container = painel.salvarContainer(new ContainerDTO(null, "Ambiente", null, true, 0, List.of()));
        var card = painel.salvarCard(new CardDTO(null, container.id(), "PROD", prod.id(), null,
                false, sistema.id(), null, null, null, null, null,
                windows ? "echo ambiente=%ENV%" : "echo ambiente=$ENV", true, 0));

        var fim = esperarFim(painel.dispararCard(card.id(), "Guilherme").id());
        assertThat(fim.state()).isEqualTo("SUCCESS");
        assertThat(painel.log(fim.id(), 0).chunk()).contains("ambiente=" + prod.key());
    }

    /** Variável com cara de senha não vai para o log: ele acaba colado no Jira. */
    @Test
    void senhaNaoApareceNoTerminal() throws Exception {
        var ambiente = catalogo.salvarAmbiente(new EnvironmentDTO(null, "Seg " + System.nanoTime(),
                null, false, "ENV=hml" + System.lineSeparator() + "TEST_PASSWORD=segredo-real",
                true, 0));
        var container = painel.salvarContainer(new ContainerDTO(null, "Segredo", null, true, 0, List.of()));
        var card = painel.salvarCard(new CardDTO(null, container.id(), "HML", ambiente.id(), null,
                false, sistema.id(), null, null, null, null, null, "echo ok", true, 0));

        esperarFim(painel.dispararCard(card.id(), "Guilherme").id());
        var log = painel.log(painel.historico().get(0).id(), 0).chunk();
        assertThat(log).contains("TEST_PASSWORD=***");
        assertThat(log).doesNotContain("segredo-real");
    }

    /**
     * A versão precisa ir para o comando, como o ambiente vai para a variável.
     *
     * No Playwright ela é "project": guardar a versão no card sem levá-la ao
     * comando faria o card dizer V3 e rodar tudo.
     */
    @Test
    void aVersaoEntraNoComando() {
        var versao = new com.tester.runmanager.entity.RunVersion();
        versao.setKey("v3");
        assertThat(RunPanelService.comVersao("npx playwright test --grep @smoke", versao, null))
                .isEqualTo("npx playwright test --grep @smoke --project=v3");
    }

    /** Quem já escreveu --project no script manda nele. */
    @Test
    void naoDuplicaOProjectQueJaEstavaNoScript() {
        var versao = new com.tester.runmanager.entity.RunVersion();
        versao.setKey("v3");
        String script = "npx playwright test --project=v2";
        assertThat(RunPanelService.comVersao(script, versao, null)).isEqualTo(script);
    }

    @Test
    void semVersaoOComandoFicaComoEstava() {
        assertThat(RunPanelService.comVersao("npx playwright test", null, null))
                .isEqualTo("npx playwright test");
    }

    /**
     * O painel precisa servir a projeto que não seja este.
     *
     * O jeito de escolher a versão é do framework, não do painel: Playwright
     * usa --project, outros usam outra coisa, e há quem não tenha nada
     * equivalente. Fixar a forma do Playwright aqui amarraria o RunManager a
     * um único projeto.
     */
    @Test
    void aFormaDeEscolherAVersaoEDoProjeto() {
        var versao = new com.tester.runmanager.entity.RunVersion();
        versao.setKey("v4");

        assertThat(RunPanelService.comVersao("npx playwright test", versao, null))
                .isEqualTo("npx playwright test --project=v4");
        assertThat(RunPanelService.comVersao("npx cypress run", versao, "--env versao={{versao}}"))
                .isEqualTo("npx cypress run --env versao=v4");
        assertThat(RunPanelService.comVersao("mvn test", versao, "-Dsuite={{versao}}"))
                .isEqualTo("mvn test -Dsuite=v4");
    }

    /** Ferramenta sem parâmetro de versão: quem resolve é o script do card. */
    @Test
    void podeDesligarOParametroDeVersao() {
        var versao = new com.tester.runmanager.entity.RunVersion();
        versao.setKey("v4");
        assertThat(RunPanelService.comVersao("meu-script.sh", versao, "nenhum"))
                .isEqualTo("meu-script.sh");
    }

    /** Versão nova no projeto entra sem ninguém tocar no painel. */
    @Test
    void versaoNovaEntraNoComandoComoQualquerOutra() {
        var v4 = new com.tester.runmanager.entity.RunVersion();
        v4.setKey("v4");
        assertThat(RunPanelService.comVersao("npx playwright test --grep @smoke", v4, null))
                .isEqualTo("npx playwright test --grep @smoke --project=v4");
    }
}
