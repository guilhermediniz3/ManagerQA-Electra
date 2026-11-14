package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.dto.TestCaseDTO;
import com.tester.entity.TestCaseEntity;
import com.tester.entity.TestSuite;
import com.tester.enuns.Status;
import com.tester.integration.IntegrationException;
import com.tester.integration.ai.AiService;
import com.tester.integration.ai.TestCaseAiProvider;
import com.tester.integration.config.IntegrationService;
import com.tester.repository.TestCaseRepository;
import com.tester.repository.TestSuiteRepository;
import com.tester.service.TestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiExplicitImportContractTest {
    private final TestCaseAiProvider provider = mock(TestCaseAiProvider.class);
    private final IntegrationService settings = mock(IntegrationService.class);
    private final TestCaseRepository repository = mock(TestCaseRepository.class);
    private final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    private final TestCaseService caseService = new TestCaseService();
    private final AiService ai = new AiService(List.of(provider), new ObjectMapper(), settings,
            caseService, suites);
    private final TestSuite suite = new TestSuite();

    @BeforeEach
    void configureNormalTestCaseService() {
        ReflectionTestUtils.setField(caseService, "testCaseRepository", repository);
        ReflectionTestUtils.setField(caseService, "testSuiteRepository", suites);
        when(provider.id()).thenReturn("fixture");
        suite.setId(7L);
    }

    @Test
    void previewsWithoutSavingAndImportsOnlyTheExplicitSelectionAsNormalEntities() {
        when(provider.generate("Acceptance criteria")).thenReturn("""
                {"cases":[
                  {"scenario":"Login succeeds","expectedResult":"Session opens"},
                  {"scenario":"Login fails","expectedResult":"Error appears"},
                  {"scenario":"Session expires","expectedResult":"Sign in again"}
                ]}
                """);

        var drafts = ai.preview("fixture", "Acceptance criteria");

        assertThat(drafts).hasSize(3);
        verifyNoInteractions(repository, suites);

        var existing = new TestCaseEntity();
        existing.setCodeCase(8L);
        suite.getCases().add(existing);
        when(suites.findById(7L)).thenReturn(Optional.of(suite));
        var id = new AtomicLong(100);
        when(repository.save(any(TestCaseEntity.class))).thenAnswer(invocation -> {
            TestCaseEntity entity = invocation.getArgument(0);
            entity.setId(id.incrementAndGet());
            return entity;
        });

        var imported = ai.importSelected(7L, List.of(drafts.get(1), drafts.get(2)));

        var saved = ArgumentCaptor.forClass(TestCaseEntity.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(entity -> {
            assertThat(entity).isExactlyInstanceOf(TestCaseEntity.class);
            assertThat(entity.getTestSuite()).isSameAs(suite);
            assertThat(entity.getStatus()).isEqualTo(Status.EM_PROGRESSO);
            assertThat(entity.getData()).isEqualTo(LocalDate.now());
            assertThat(entity.getObtainedResult()).isNull();
            assertThat(entity.getVideoEvidence()).isNull();
        });
        assertThat(saved.getAllValues()).extracting(TestCaseEntity::getScenario)
                .containsExactly("Login fails", "Session expires");
        assertThat(saved.getAllValues()).extracting(TestCaseEntity::getExpectedResult)
                .containsExactly("Error appears", "Sign in again");
        assertThat(imported).extracting(TestCaseDTO::getCodeCase).containsExactly(9L, 10L);
        assertThat(imported).extracting(TestCaseDTO::getTestSuiteId).containsOnly(7L);
        assertThat(imported).extracting(TestCaseDTO::getId).containsExactly(101L, 102L);
        verify(provider, times(1)).generate("Acceptance criteria");
    }

    @Test
    void rejectsTheWholeInvalidSelectionBeforePersistingAnyValidPrecedingDraft() {
        var valid = new AiService.Draft("Valid scenario", "Expected outcome");
        var invalid = new AiService.Draft("", "Expected outcome");

        assertThatThrownBy(() -> ai.importSelected(7L, List.of(valid, invalid)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository, suites);
        verify(provider, never()).generate(anyString());
    }

    @Test
    void anUnknownSuiteCannotSaveDraftsOrCallAnAiProvider() {
        when(suites.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ai.importSelected(404L,
                List.of(new AiService.Draft("Valid scenario", "Expected outcome"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Suíte inválida");

        verifyNoInteractions(repository);
        verify(provider, never()).generate(anyString());
    }

    /**
     * Caso fora do formato é descartado sem levar os outros junto: recusar o
     * lote inteiro por causa de uma linha joga fora minutos de geração — e na
     * IA local são minutos de verdade. Nada é gravado de qualquer forma: a
     * prévia só vira registro na importação explícita.
     */
    @Test
    void malformedCaseIsDroppedAndTheGoodOnesSurvive() {
        when(provider.generate("Acceptance criteria")).thenReturn(
                "{\"cases\":[{\"scenario\":\"Valid\",\"expectedResult\":\"Yes\"},{\"scenario\":123}]}");

        var drafts = ai.preview("fixture", "Acceptance criteria");
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).scenario()).isEqualTo("Valid");

        verifyNoInteractions(repository, suites);
    }

    @Test
    void aResponseWithNoUsableCaseStillFails() {
        when(provider.generate("Acceptance criteria")).thenReturn(
                "{\"cases\":[{\"scenario\":123},{\"scenario\":\"S\",\"expectedResult\":\"\"}]}");

        assertThatThrownBy(() -> ai.preview("fixture", "Acceptance criteria"))
                .isInstanceOf(IntegrationException.class).hasMessageContaining("Nenhum caso foi salvo");

        verifyNoInteractions(repository, suites);
    }

    /** Texto corrido no lugar da lista tem mensagem própria: diz o que houve. */
    @Test
    void proseInsteadOfCasesExplainsItself() {
        when(provider.generate("Acceptance criteria"))
                .thenReturn("Claro! Vou gerar os casos de teste para você.");

        assertThatThrownBy(() -> ai.preview("fixture", "Acceptance criteria"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("texto corrido");

        verifyNoInteractions(repository, suites);
    }

    @Test
    void refusesAnEmptyOrOversizedSelectionWithoutSaving() {
        assertThatThrownBy(() -> ai.importSelected(7L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ai.importSelected(7L, java.util.Arrays.asList((AiService.Draft)null)))
                .isInstanceOf(IllegalArgumentException.class);
        var tooMany = java.util.Collections.nCopies(51, new AiService.Draft("Scenario", "Expected"));
        assertThatThrownBy(() -> ai.importSelected(7L, tooMany))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository, suites);
    }
}
