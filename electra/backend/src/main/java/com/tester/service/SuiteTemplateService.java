package com.tester.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.dto.SuiteTemplateDTO;
import com.tester.dto.TemplateCaseDTO;
import com.tester.entity.SuiteTemplate;
import com.tester.entity.TemplateCase;
import com.tester.entity.TestCaseEntity;
import com.tester.entity.TestSuite;
import com.tester.enuns.Status;
import com.tester.repository.SuiteTemplateRepository;
import com.tester.repository.SystemModuleRepository;
import com.tester.repository.TestSuiteRepository;

/**
 * Templates de suíte: montar um molde de casos e reaproveitá-lo.
 *
 * São dois caminhos, um espelho do outro. Uma suíte que ficou boa vira template
 * ({@link #fromSuite}) e um template vira casos dentro de outra suíte
 * ({@link #importInto}). Nos dois sentidos anda só o roteiro — cenário e
 * resultado esperado —, porque resultado obtido, evidência e status pertencem à
 * execução daquela suíte, não ao molde.
 */
@Service
public class SuiteTemplateService {

    private final SuiteTemplateRepository templates;
    private final TestSuiteRepository suites;
    private final SystemModuleRepository modules;

    public SuiteTemplateService(SuiteTemplateRepository templates, TestSuiteRepository suites,
                                SystemModuleRepository modules) {
        this.templates = templates;
        this.suites = suites;
        this.modules = modules;
    }

    /**
     * Lista completa, sem paginar no servidor: a paginacao deste projeto e feita
     * na tela (usePaged), e um catalogo de templates nao chega perto de um volume
     * que justifique quebrar esse padrao.
     */
    @Transactional(readOnly = true)
    public List<SuiteTemplateDTO> listar() {
        return templates.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(SuiteTemplateDTO::resumo).toList();
    }

    /** So os ativos, para o seletor dentro da suite. */
    @Transactional(readOnly = true)
    public List<SuiteTemplateDTO> disponiveis() {
        return templates.findByActiveTrueOrderByNameAsc().stream().map(SuiteTemplateDTO::resumo).toList();
    }

    @Transactional(readOnly = true)
    public SuiteTemplateDTO buscar(Long id) {
        return SuiteTemplateDTO.completo(exigir(id));
    }

    @Transactional
    public SuiteTemplateDTO criar(SuiteTemplateDTO dto, String autor) {
        SuiteTemplate template = new SuiteTemplate();
        template.setCreatedBy(autor);
        template.setData(dto.getData() == null ? LocalDate.now() : dto.getData());
        aplicar(template, dto);
        return SuiteTemplateDTO.completo(templates.save(template));
    }

    @Transactional
    public SuiteTemplateDTO atualizar(Long id, SuiteTemplateDTO dto) {
        SuiteTemplate template = exigir(id);
        if (dto.getData() != null) template.setData(dto.getData());
        aplicar(template, dto);
        return SuiteTemplateDTO.completo(templates.save(template));
    }

    @Transactional
    public void excluir(Long id) {
        templates.delete(exigir(id));
    }

    /**
     * Cria um template a partir de uma suíte já executada. O nome vem de quem
     * pediu; sem nome, herda o do plano, que costuma descrever o que a suíte faz.
     */
    @Transactional
    public SuiteTemplateDTO fromSuite(Long suiteId, SuiteTemplateDTO dto, String autor) {
        TestSuite suite = suites.findById(suiteId)
                .orElseThrow(() -> new IllegalArgumentException("Suíte não encontrada."));
        if (suite.getCases().isEmpty())
            throw new IllegalArgumentException("Esta suíte não tem casos de teste para virar template.");

        SuiteTemplate template = new SuiteTemplate();
        template.setCreatedBy(autor);
        template.setData(LocalDate.now());
        template.setName(nomeOuPadrao(dto, suite));
        template.setDescription(dto == null ? null : dto.getDescription());
        template.setActive(true);
        if (dto != null && dto.getSystemModuleId() != null) {
            template.setSystemModule(modules.findById(dto.getSystemModuleId()).orElse(null));
        } else if (suite.getTestPlan() != null) {
            template.setSystemModule(suite.getTestPlan().getSystemModule());
        }

        List<TestCaseEntity> ordenados = new ArrayList<>(suite.getCases());
        ordenados.sort(Comparator.comparing(TestCaseEntity::getCodeCase,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TestCaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        int posicao = 0;
        for (TestCaseEntity caso : ordenados) {
            TemplateCase molde = new TemplateCase();
            molde.setTemplate(template);
            molde.setCodeCase(caso.getCodeCase());
            molde.setScenario(caso.getScenario());
            molde.setExpectedResult(caso.getExpectedResult());
            molde.setPosition(posicao++);
            template.getCases().add(molde);
        }
        return SuiteTemplateDTO.completo(templates.save(template));
    }

    /**
     * Acrescenta os casos do template à suíte, sem apagar o que já existe — quem
     * importa costuma estar somando um roteiro padrão aos casos próprios da
     * demanda. Os códigos continuam de onde a suíte parou, para não duplicar.
     */
    @Transactional
    public int importInto(Long suiteId, Long templateId) {
        TestSuite suite = suites.findById(suiteId)
                .orElseThrow(() -> new IllegalArgumentException("Suíte não encontrada."));
        SuiteTemplate template = exigir(templateId);
        if (template.getCases().isEmpty())
            throw new IllegalArgumentException("Este template não tem casos de teste para importar.");

        long proximoCodigo = suite.getCases().stream()
                .map(TestCaseEntity::getCodeCase)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;

        for (TemplateCase molde : template.getCases()) {
            TestCaseEntity caso = new TestCaseEntity();
            caso.setTestSuite(suite);
            caso.setCodeCase(proximoCodigo++);
            caso.setScenario(molde.getScenario());
            caso.setExpectedResult(molde.getExpectedResult());
            // Execução começa limpa: é a suíte que vai ser executada, não o molde.
            caso.setObtainedResult(null);
            caso.setVideoEvidence(null);
            caso.setStatus(Status.EM_PROGRESSO);
            caso.setData(suite.getData() == null ? LocalDate.now() : suite.getData());
            suite.getCases().add(caso);
        }
        suites.save(suite);
        return template.getCases().size();
    }

    private String nomeOuPadrao(SuiteTemplateDTO dto, TestSuite suite) {
        if (dto != null && dto.getName() != null && !dto.getName().isBlank()) return dto.getName().trim();
        String plano = suite.getTestPlan() == null ? null : suite.getTestPlan().getName();
        if (plano != null && !plano.isBlank()) return plano.trim();
        return "Template da suíte " + suite.getCodeSuite();
    }

    private void aplicar(SuiteTemplate template, SuiteTemplateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new IllegalArgumentException("Dê um nome ao template.");
        template.setName(dto.getName().trim());
        template.setDescription(dto.getDescription());
        template.setActive(dto.isActive());
        template.setSystemModule(dto.getSystemModuleId() == null ? null
                : modules.findById(dto.getSystemModuleId()).orElse(null));

        // Substitui a lista inteira: a tela envia o roteiro como ele ficou, e
        // orphanRemoval cuida de apagar o que saiu.
        template.getCases().clear();
        int posicao = 0;
        for (TemplateCaseDTO caso : dto.getCases()) {
            if ((caso.getScenario() == null || caso.getScenario().isBlank())
                    && (caso.getExpectedResult() == null || caso.getExpectedResult().isBlank())) {
                continue; // linha em branco que ficou na tela
            }
            TemplateCase molde = new TemplateCase();
            molde.setTemplate(template);
            molde.setCodeCase(caso.getCodeCase() == null ? posicao + 1L : caso.getCodeCase());
            molde.setScenario(caso.getScenario());
            molde.setExpectedResult(caso.getExpectedResult());
            molde.setPosition(posicao++);
            template.getCases().add(molde);
        }
        if (template.getCases().isEmpty())
            throw new IllegalArgumentException("Um template precisa de pelo menos um caso de teste.");
    }

    private SuiteTemplate exigir(Long id) {
        return templates.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template não encontrado."));
    }
}
