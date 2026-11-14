package com.tester;

import java.util.List;
import com.tester.demand.DemandRepository;
import com.tester.demand.DemandService;
import com.tester.demand.Demand;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.jira.service.JiraIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:demand_ai_summary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class DemandAiSummaryTest {
    @Autowired DemandService service;
    @Autowired DemandRepository repository;
    @MockitoBean JiraIssueService jira;
    @MockitoBean IntegrationService settings;

    @BeforeEach
    void seedDemand() {
        repository.deleteAll();
        var demand = new Demand();
        demand.jiraKey = "SPROD-134";
        demand.summary = "DRE por Caixa";
        demand.description = "Gera o relatório de Planos de Contas.";
        repository.save(demand);
    }

    private DemandService.AiSummaryInput input(String summary, List<String> points, List<String> questions) {
        return new DemandService.AiSummaryInput("groq", summary, points, questions);
    }

    @Test
    void persistsSummaryAndReadsItBack() {
        var saved = service.saveAiSummary("SPROD-134",
            input("A demanda cria um novo endpoint.", List.of("Validar datas"), List.of("Qual empresa?")));
        assertThat(saved.aiSummary().summary()).isEqualTo("A demanda cria um novo endpoint.");
        assertThat(saved.aiSummary().provider()).isEqualTo("groq");
        assertThat(saved.aiSummary().savedAt()).isNotNull();

        var reloaded = service.get("SPROD-134").aiSummary();
        assertThat(reloaded.summary()).isEqualTo("A demanda cria um novo endpoint.");
        assertThat(reloaded.points()).containsExactly("Validar datas");
        assertThat(reloaded.questions()).containsExactly("Qual empresa?");
    }

    @Test
    void absentSummaryReadsAsNull() {
        assertThat(service.get("SPROD-134").aiSummary()).isNull();
    }

    @Test
    void capsListsAndSkipsBlankItems() {
        var saved = service.saveAiSummary("SPROD-134", input("Resumo.",
            List.of("1", "  ", "2", "3", "4", "5", "6", "7"), List.of("a", "b", "c", "d", "e")));
        assertThat(saved.aiSummary().points()).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(saved.aiSummary().questions()).containsExactly("a", "b", "c", "d");
    }

    @Test
    void rejectsBlankSummary() {
        assertThatThrownBy(() -> service.saveAiSummary("SPROD-134", input("   ", List.of(), List.of())))
            .hasMessageContaining("Gere o resumo antes de salvar");
    }

    @Test
    void rejectsOversizedItem() {
        assertThatThrownBy(() -> service.saveAiSummary("SPROD-134",
            input("Resumo.", List.of("x".repeat(501)), List.of())))
            .hasMessageContaining("até 500 caracteres");
    }

    @Test
    void keepsSummaryWhenLocalDataChanges() {
        service.saveAiSummary("SPROD-134", input("Resumo preservado.", List.of(), List.of()));
        var demand = repository.findById("SPROD-134").orElseThrow();
        demand.callNumber = "101657";
        repository.save(demand);
        assertThat(service.get("SPROD-134").aiSummary().summary()).isEqualTo("Resumo preservado.");
    }
}
