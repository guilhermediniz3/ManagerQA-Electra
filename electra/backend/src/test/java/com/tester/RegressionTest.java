package com.tester;

import com.tester.dto.RegressionDTO;
import com.tester.dto.RegressionItemDTO;
import com.tester.entity.*;
import com.tester.enuns.Status;
import com.tester.repository.RegressionRepository;
import com.tester.repository.SystemModuleRepository;
import com.tester.repository.TestPlanRepository;
import com.tester.repository.TesterQARepository;
import com.tester.service.RegressionService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regressão: montar a rodada a partir do que fechou, e clonar sem levar junto o
 * resultado da rodada anterior.
 */
class RegressionTest {

    final RegressionRepository regressions = mock(RegressionRepository.class);
    final TestPlanRepository plans = mock(TestPlanRepository.class);
    final SystemModuleRepository modules = mock(SystemModuleRepository.class);
    final TesterQARepository testers = mock(TesterQARepository.class);
    final RegressionService service = new RegressionService(regressions, plans, modules, testers);

    RegressionTest() {
        when(regressions.save(any(Regression.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    static SystemModule modulo(long id, String nome) {
        SystemModule modulo = new SystemModule();
        modulo.setId(id);
        modulo.setName(nome);
        return modulo;
    }

    static TestPlan plano(String nome, String jira, SystemModule modulo, Status status, LocalDate data) {
        TestPlan plan = new TestPlan();
        plan.setName(nome);
        plan.setJira(jira);
        plan.setSystemModule(modulo);
        plan.setStatus(status);
        plan.setData(data);
        plan.setObservation("Observação de " + jira);
        return plan;
    }

    static RegressionItemDTO item(Long moduloId, String descricao, String fluxo) {
        RegressionItemDTO dto = new RegressionItemDTO();
        dto.setSystemModuleId(moduloId);
        dto.setDescription(descricao);
        dto.setFlow(fluxo);
        return dto;
    }

    // ── Buscar módulos aprovados ──────────────────────────────────────────────

    @Test void bringsModulesFromFinishedPlansWithTitleAndObservation() {
        SystemModule fiscal = modulo(1L, "Fiscal");
        when(plans.findAll()).thenReturn(List.of(
                plano("Retirar ICMS da nota de crédito", "SOP-260", fiscal, Status.CONCLUIDA, LocalDate.of(2026, 9, 10)),
                plano("Em andamento", "SOP-999", fiscal, Status.EM_PROGRESSO, LocalDate.of(2026, 9, 11))));

        List<RegressionItemDTO> aprovados = service.aprovados(null, null);

        assertThat(aprovados).hasSize(1);
        assertThat(aprovados.get(0).getSystemModuleName()).isEqualTo("Fiscal");
        assertThat(aprovados.get(0).getDescription()).isEqualTo("Retirar ICMS da nota de crédito");
        assertThat(aprovados.get(0).getObservation()).isEqualTo("Observação de SOP-260");
        assertThat(aprovados.get(0).getStatus()).isEqualTo(Status.CONCLUIDA);
        assertThat(aprovados.get(0).getJiraKey()).isEqualTo("SOP-260");
    }

    /** Regressão é por módulo: duas demandas no mesmo módulo viram uma linha só. */
    @Test void groupsSeveralDemandsOfTheSameModuleIntoOneRow() {
        SystemModule fiscal = modulo(1L, "Fiscal");
        when(plans.findAll()).thenReturn(List.of(
                plano("Nota de crédito", "SOP-260", fiscal, Status.CONCLUIDA, LocalDate.of(2026, 9, 10)),
                plano("Nota de débito", "SOP-261", fiscal, Status.CONCLUIDA, LocalDate.of(2026, 9, 11))));

        List<RegressionItemDTO> aprovados = service.aprovados(null, null);

        assertThat(aprovados).hasSize(1);
        assertThat(aprovados.get(0).getDescription()).contains("Nota de crédito", "Nota de débito");
        assertThat(aprovados.get(0).getJiraKey()).contains("SOP-260", "SOP-261");
    }

    /** Uma demanda em retorno já basta para o módulo inteiro merecer atenção. */
    @Test void returnStatusWinsOverConcludedInTheSameModule() {
        SystemModule fiscal = modulo(1L, "Fiscal");
        when(plans.findAll()).thenReturn(List.of(
                plano("Concluída", "SOP-1", fiscal, Status.CONCLUIDA, LocalDate.of(2026, 9, 10)),
                plano("Devolvida", "SOP-2", fiscal, Status.RETORNO, LocalDate.of(2026, 9, 11))));

        assertThat(service.aprovados(null, null).get(0).getStatus()).isEqualTo(Status.RETORNO);
    }

    @Test void respectsTheChosenPeriod() {
        SystemModule fiscal = modulo(1L, "Fiscal");
        SystemModule estoque = modulo(2L, "Estoque");
        when(plans.findAll()).thenReturn(List.of(
                plano("Antiga", "SOP-1", fiscal, Status.CONCLUIDA, LocalDate.of(2026, 8, 1)),
                plano("Do período", "SOP-2", estoque, Status.CONCLUIDA, LocalDate.of(2026, 9, 10))));

        List<RegressionItemDTO> aprovados =
                service.aprovados(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(aprovados).hasSize(1);
        assertThat(aprovados.get(0).getSystemModuleName()).isEqualTo("Estoque");
    }

    /** Plano sem módulo não tem o que revalidar. */
    @Test void skipsPlansWithoutModule() {
        when(plans.findAll()).thenReturn(List.of(
                plano("Sem módulo", "SOP-1", null, Status.CONCLUIDA, LocalDate.of(2026, 9, 10))));
        assertThat(service.aprovados(null, null)).isEmpty();
    }

    // ── Salvar ────────────────────────────────────────────────────────────────

    @Test void savesModulesInTheOrderTheyWereArranged() {
        when(modules.findById(1L)).thenReturn(Optional.of(modulo(1L, "Fiscal")));
        when(modules.findById(2L)).thenReturn(Optional.of(modulo(2L, "Estoque")));

        RegressionDTO dto = new RegressionDTO();
        dto.setName("Regressão de setembro");
        dto.setItems(List.of(item(1L, "Fiscal mexeu", "Emitir nota"), item(2L, "Estoque mexeu", "Dar entrada")));

        RegressionDTO salva = service.criar(dto, "Guilherme");

        assertThat(salva.getItems()).hasSize(2);
        assertThat(salva.getItems().get(0).getSystemModuleName()).isEqualTo("Fiscal");
        assertThat(salva.getItems().get(1).getSystemModuleName()).isEqualTo("Estoque");
        assertThat(salva.getCreatedBy()).isEqualTo("Guilherme");
        assertThat(salva.getData()).isEqualTo(LocalDate.now());
    }

    @Test void assignsQaPerModule() {
        TesterQA ana = new TesterQA();
        ana.setId(5L);
        ana.setName("Ana");
        when(modules.findById(1L)).thenReturn(Optional.of(modulo(1L, "Fiscal")));
        when(testers.findById(5L)).thenReturn(Optional.of(ana));

        RegressionItemDTO entrada = item(1L, "Fiscal", "Emitir nota");
        entrada.setTesterId(5L);
        entrada.setStatus(Status.EM_PROGRESSO);

        RegressionDTO dto = new RegressionDTO();
        dto.setName("Regressão");
        dto.setItems(List.of(entrada));

        RegressionDTO salva = service.criar(dto, "Guilherme");
        assertThat(salva.getItems().get(0).getTesterName()).isEqualTo("Ana");
        assertThat(salva.getItems().get(0).getStatus()).isEqualTo(Status.EM_PROGRESSO);
    }

    @Test void requiresName() {
        RegressionDTO semNome = new RegressionDTO();
        semNome.setItems(List.of(item(1L, "x", "y")));
        assertThatThrownBy(() -> service.criar(semNome, "Guilherme")).hasMessageContaining("nome");
    }

    /**
     * A rodada nasce vazia e ganha os módulos depois, pela busca dos aprovados
     * ou à mão. Exigir módulo já na criação impedia esse caminho — e ele é o
     * normal, porque ninguém sabe o que revalidar antes de olhar o que mudou.
     */
    @Test void startsEmptyAndGetsModulesLater() {
        RegressionDTO nova = new RegressionDTO();
        nova.setName("Regressão de setembro");

        RegressionDTO criada = service.criar(nova, "Guilherme");
        assertThat(criada.getItems()).isEmpty();
        assertThat(criada.getName()).isEqualTo("Regressão de setembro");
    }

    @Test void blankRowsAreDropped() {
        when(modules.findById(1L)).thenReturn(Optional.of(modulo(1L, "Fiscal")));
        RegressionDTO dto = new RegressionDTO();
        dto.setName("Regressão");
        dto.setItems(List.of(new RegressionItemDTO(), item(1L, "Vale", "Validar"), new RegressionItemDTO()));

        assertThat(service.criar(dto, "Guilherme").getItems()).hasSize(1);
    }

    // ── Clonar ────────────────────────────────────────────────────────────────

    /** O clone leva o roteiro e deixa para trás o resultado: status e QA. */
    @Test void cloneKeepsTheScriptAndClearsStatusAndQa() {
        TesterQA ana = new TesterQA();
        ana.setId(5L);
        ana.setName("Ana");

        Regression origem = new Regression();
        origem.setId(3L);
        origem.setName("Regressão de agosto");
        origem.setObservation("Rodada mensal");
        RegressionItem item = new RegressionItem();
        item.setRegression(origem);
        item.setSystemModule(modulo(1L, "Fiscal"));
        item.setDescription("Nota de crédito");
        item.setObservation("Ticket 892774");
        item.setFlow("Emitir nota com finalidade 5");
        item.setJiraKey("SOP-260");
        item.setStatus(Status.CONCLUIDA);
        item.setTester(ana);
        item.setPosition(0);
        origem.getItems().add(item);
        when(regressions.findById(3L)).thenReturn(Optional.of(origem));

        RegressionDTO copia = service.clonar(3L, null, "Guilherme");

        assertThat(copia.getItems()).hasSize(1);
        RegressionItemDTO clonado = copia.getItems().get(0);
        assertThat(clonado.getSystemModuleName()).isEqualTo("Fiscal");
        assertThat(clonado.getDescription()).isEqualTo("Nota de crédito");
        assertThat(clonado.getObservation()).isEqualTo("Ticket 892774");
        assertThat(clonado.getFlow()).isEqualTo("Emitir nota com finalidade 5");
        assertThat(clonado.getJiraKey()).isEqualTo("SOP-260");
        // O que muda:
        assertThat(clonado.getStatus()).isNull();
        assertThat(clonado.getTesterId()).isNull();
        assertThat(clonado.getTesterName()).isNull();
        // E a rodada original continua intacta.
        assertThat(origem.getItems().get(0).getStatus()).isEqualTo(Status.CONCLUIDA);
        assertThat(origem.getItems().get(0).getTester()).isEqualTo(ana);
    }

    @Test void cloneGetsTodaysDateAndATellingName() {
        Regression origem = new Regression();
        origem.setId(3L);
        origem.setName("Regressão de agosto");
        RegressionItem item = new RegressionItem();
        item.setRegression(origem);
        item.setDescription("Algo");
        origem.getItems().add(item);
        when(regressions.findById(3L)).thenReturn(Optional.of(origem));

        RegressionDTO copia = service.clonar(3L, null, "Guilherme");

        assertThat(copia.getData()).isEqualTo(LocalDate.now());
        assertThat(copia.getName()).startsWith("Regressão de agosto (cópia de ");
        assertThat(copia.getCreatedBy()).isEqualTo("Guilherme");
    }

    @Test void cloneAcceptsAChosenNameAndDate() {
        Regression origem = new Regression();
        origem.setId(3L);
        origem.setName("Antiga");
        RegressionItem item = new RegressionItem();
        item.setRegression(origem);
        item.setDescription("Algo");
        origem.getItems().add(item);
        when(regressions.findById(3L)).thenReturn(Optional.of(origem));

        RegressionDTO escolha = new RegressionDTO();
        escolha.setName("Regressão de outubro");
        escolha.setData(LocalDate.of(2026, 10, 1));

        RegressionDTO copia = service.clonar(3L, escolha, "Guilherme");
        assertThat(copia.getName()).isEqualTo("Regressão de outubro");
        assertThat(copia.getData()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    // ── Listagem ──────────────────────────────────────────────────────────────

    @Test void listingCountsWhatIsDoneAndWhatIsLeft() {
        Regression regression = new Regression();
        regression.setId(1L);
        regression.setName("Setembro");
        regression.setData(LocalDate.of(2026, 9, 15));
        for (Status status : new Status[] { Status.CONCLUIDA, Status.CONCLUIDA, Status.RETORNO, null }) {
            RegressionItem item = new RegressionItem();
            item.setRegression(regression);
            item.setStatus(status);
            regression.getItems().add(item);
        }
        when(regressions.findAllByOrderByDataDescIdDesc()).thenReturn(List.of(regression));

        RegressionDTO resumo = service.listar(null, null).get(0);
        assertThat(resumo.getTotalItems()).isEqualTo(4);
        assertThat(resumo.getTotalConcluidos()).isEqualTo(2);
        assertThat(resumo.getTotalPendentes()).isEqualTo(2);
        assertThat(resumo.getItems()).isEmpty(); // a listagem não carrega os itens
    }

    /**
     * Cada recorte de período usa a consulta correspondente.
     *
     * Uma consulta só, testando se o parâmetro é nulo, passava aqui e era
     * recusada pelo Postgres — o filtro por data chegou quebrado ao uso real
     * justamente porque o banco do teste era mais tolerante que o de verdade.
     */
    @Test void cadaPeriodoUsaSuaConsulta() {
        var de = java.time.LocalDate.of(2026, 9, 1);
        var ate = java.time.LocalDate.of(2026, 9, 30);

        service.listar(de, ate);
        verify(regressions).findByDataBetweenOrderByDataDescIdDesc(de, ate);

        service.listar(de, null);
        verify(regressions).findByDataGreaterThanEqualOrderByDataDescIdDesc(de);

        service.listar(null, ate);
        verify(regressions).findByDataLessThanEqualOrderByDataDescIdDesc(ate);

        service.listar(null, null);
        verify(regressions).findAllByOrderByDataDescIdDesc();
    }
}
