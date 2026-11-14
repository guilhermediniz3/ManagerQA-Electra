package com.tester;

import com.tester.dto.SuiteTemplateDTO;
import com.tester.dto.TemplateCaseDTO;
import com.tester.entity.*;
import com.tester.enuns.Status;
import com.tester.repository.SuiteTemplateRepository;
import com.tester.repository.SystemModuleRepository;
import com.tester.repository.TestSuiteRepository;
import com.tester.service.SuiteTemplateService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Templates de suíte. O que precisa ficar de pé nos dois sentidos: só o roteiro
 * anda, e a execução da suíte de destino nunca vem carimbada do molde.
 */
class SuiteTemplateTest {

    final SuiteTemplateRepository templates = mock(SuiteTemplateRepository.class);
    final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    final SystemModuleRepository modules = mock(SystemModuleRepository.class);
    final SuiteTemplateService service = new SuiteTemplateService(templates, suites, modules);

    final TestPlan plan = new TestPlan();
    final TestSuite suite = new TestSuite();

    SuiteTemplateTest() {
        plan.setId(7L);
        plan.setName("Emissão de nota de crédito");
        suite.setId(9L);
        suite.setCodeSuite(1L);
        suite.setTestPlan(plan);
        suite.setData(LocalDate.of(2026, 9, 15));
        when(suites.findById(9L)).thenReturn(Optional.of(suite));
        when(templates.save(any(SuiteTemplate.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(suites.save(any(TestSuite.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    static TestCaseEntity caso(long id, long codigo, String cenario) {
        TestCaseEntity caso = new TestCaseEntity();
        caso.setId(id);
        caso.setCodeCase(codigo);
        caso.setScenario(cenario);
        caso.setExpectedResult("Esperado " + codigo);
        caso.setObtainedResult("Obtido " + codigo);
        caso.setVideoEvidence("https://vimeo.com/" + codigo);
        caso.setStatus(Status.CONCLUIDA);
        return caso;
    }

    static SuiteTemplate template(String nome, String... cenarios) {
        SuiteTemplate template = new SuiteTemplate();
        template.setId(3L);
        template.setName(nome);
        int posicao = 0;
        for (String cenario : cenarios) {
            TemplateCase molde = new TemplateCase();
            molde.setTemplate(template);
            molde.setCodeCase(posicao + 1L);
            molde.setScenario(cenario);
            molde.setExpectedResult("Esperado de " + cenario);
            molde.setPosition(posicao++);
            template.getCases().add(molde);
        }
        return template;
    }

    /** Suíte executada vira molde: vai o roteiro, fica a execução. */
    @Test void suiteBecomesTemplateCarryingOnlyTheScript() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 2L, "Segundo"), caso(2L, 1L, "Primeiro"))));

        SuiteTemplateDTO criado = service.fromSuite(9L, null, "Guilherme");

        assertThat(criado.getName()).isEqualTo("Emissão de nota de crédito");
        assertThat(criado.getCreatedBy()).isEqualTo("Guilherme");
        assertThat(criado.getCases()).hasSize(2);
        // A ordem segue o código do caso, não o acaso do HashSet.
        assertThat(criado.getCases().get(0).getScenario()).isEqualTo("Primeiro");
        assertThat(criado.getCases().get(1).getScenario()).isEqualTo("Segundo");
        assertThat(criado.getCases().get(0).getExpectedResult()).isEqualTo("Esperado 1");
    }

    @Test void refusesToTurnAnEmptySuiteIntoTemplate() {
        suite.setCases(new HashSet<>());
        assertThatThrownBy(() -> service.fromSuite(9L, null, "Guilherme"))
                .hasMessageContaining("não tem casos de teste");
    }

    /**
     * Importar soma ao que a suíte já tem: quem importa está acrescentando o
     * roteiro padrão aos casos próprios da demanda, não substituindo-os.
     */
    @Test void importAddsToExistingCasesAndContinuesNumbering() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Caso próprio"))));
        when(templates.findById(3L)).thenReturn(Optional.of(template("Padrão", "Login", "Logout")));

        int importados = service.importInto(9L, 3L);

        assertThat(importados).isEqualTo(2);
        assertThat(suite.getCases()).hasSize(3);
        List<Long> codigos = suite.getCases().stream().map(TestCaseEntity::getCodeCase).sorted().toList();
        assertThat(codigos).containsExactly(1L, 2L, 3L);
    }

    /** Execução nasce limpa na suíte de destino. */
    @Test void importedCasesStartWithEmptyExecution() {
        suite.setCases(new HashSet<>());
        when(templates.findById(3L)).thenReturn(Optional.of(template("Padrão", "Login")));

        service.importInto(9L, 3L);

        TestCaseEntity importado = suite.getCases().iterator().next();
        assertThat(importado.getScenario()).isEqualTo("Login");
        assertThat(importado.getExpectedResult()).isEqualTo("Esperado de Login");
        assertThat(importado.getObtainedResult()).isNull();
        assertThat(importado.getVideoEvidence()).isNull();
        assertThat(importado.getStatus()).isEqualTo(Status.EM_PROGRESSO);
        assertThat(importado.getData()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test void refusesToImportAnEmptyTemplate() {
        suite.setCases(new HashSet<>());
        when(templates.findById(3L)).thenReturn(Optional.of(template("Vazio")));
        assertThatThrownBy(() -> service.importInto(9L, 3L))
                .hasMessageContaining("não tem casos de teste");
    }

    /** Ida e volta: o que sai da suíte e volta pelo template é o mesmo roteiro. */
    @Test void roundTripKeepsTheScriptIntact() {
        suite.setCases(new HashSet<>(List.of(caso(1L, 1L, "Emitir nota"))));
        SuiteTemplateDTO criado = service.fromSuite(9L, null, "Guilherme");

        SuiteTemplate salvo = template("Padrão");
        salvo.getCases().clear();
        for (TemplateCaseDTO caso : criado.getCases()) {
            TemplateCase molde = new TemplateCase();
            molde.setTemplate(salvo);
            molde.setScenario(caso.getScenario());
            molde.setExpectedResult(caso.getExpectedResult());
            molde.setCodeCase(caso.getCodeCase());
            salvo.getCases().add(molde);
        }
        when(templates.findById(3L)).thenReturn(Optional.of(salvo));

        TestSuite destino = new TestSuite();
        destino.setId(11L);
        destino.setTestPlan(plan);
        destino.setCases(new HashSet<>());
        when(suites.findById(11L)).thenReturn(Optional.of(destino));

        service.importInto(11L, 3L);

        TestCaseEntity chegou = destino.getCases().iterator().next();
        assertThat(chegou.getScenario()).isEqualTo("Emitir nota");
        assertThat(chegou.getExpectedResult()).isEqualTo("Esperado 1");
    }

    @Test void requiresNameAndAtLeastOneCaseWhenSavingByHand() {
        SuiteTemplateDTO semNome = new SuiteTemplateDTO();
        semNome.setCases(List.of(new TemplateCaseDTO()));
        assertThatThrownBy(() -> service.criar(semNome, "Guilherme")).hasMessageContaining("nome");

        SuiteTemplateDTO semCaso = new SuiteTemplateDTO();
        semCaso.setName("Só o nome");
        assertThatThrownBy(() -> service.criar(semCaso, "Guilherme"))
                .hasMessageContaining("pelo menos um caso");
    }

    /** Linha em branco esquecida na tela não vira caso de teste. */
    @Test void blankRowsAreDropped() {
        SuiteTemplateDTO dto = new SuiteTemplateDTO();
        dto.setName("Padrão de login");
        TemplateCaseDTO vazio = new TemplateCaseDTO();
        TemplateCaseDTO preenchido = new TemplateCaseDTO();
        preenchido.setScenario("Entrar com credenciais válidas");
        dto.setCases(List.of(vazio, preenchido, new TemplateCaseDTO()));

        SuiteTemplateDTO criado = service.criar(dto, "Guilherme");

        assertThat(criado.getCases()).hasSize(1);
        assertThat(criado.getCases().get(0).getScenario()).isEqualTo("Entrar com credenciais válidas");
    }
}
