package com.tester.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.dto.GherkinFeatureDTO;
import com.tester.entity.GherkinFeature;
import com.tester.repository.GherkinFeatureRepository;
import com.tester.repository.TestPlanRepository;

/**
 * Arquivos de Gherkin de um plano de teste.
 *
 * O conteúdo é texto puro e é gravado como veio: em Gherkin a indentação e as
 * palavras-chave são a sintaxe, e qualquer "arrumação" no caminho quebraria o
 * arquivo para quem o executa.
 */
@Service
public class GherkinFeatureService {

    private final GherkinFeatureRepository features;
    private final TestPlanRepository plans;
    private final com.tester.repository.TestSuiteRepository suites;
    private final SuiteDocService docs;

    public GherkinFeatureService(GherkinFeatureRepository features, TestPlanRepository plans,
                                 com.tester.repository.TestSuiteRepository suites, SuiteDocService docs) {
        this.features = features;
        this.plans = plans;
        this.suites = suites;
        this.docs = docs;
    }

    @Transactional(readOnly = true)
    public List<GherkinFeatureDTO> porPlano(Long testPlanId) {
        return features.findByTestPlanIdOrderByIdDesc(testPlanId).stream()
                .map(GherkinFeatureDTO::resumo).toList();
    }

    @Transactional(readOnly = true)
    public GherkinFeatureDTO buscar(Long id) {
        return GherkinFeatureDTO.completo(exigir(id));
    }

    @Transactional
    public GherkinFeatureDTO criar(GherkinFeatureDTO dto, String autor) {
        GherkinFeature feature = new GherkinFeature();
        feature.setCreatedBy(autor);
        feature.setData(dto.getData() == null ? LocalDate.now() : dto.getData());
        feature.setSuiteId(dto.getSuiteId());
        if (dto.getTestPlanId() != null) {
            feature.setTestPlan(plans.findById(dto.getTestPlanId())
                    .orElseThrow(() -> new IllegalArgumentException("Plano de teste não encontrado.")));
        }
        aplicar(feature, dto);
        return GherkinFeatureDTO.completo(features.save(feature));
    }

    @Transactional
    public GherkinFeatureDTO atualizar(Long id, GherkinFeatureDTO dto) {
        GherkinFeature feature = exigir(id);
        aplicar(feature, dto);
        return GherkinFeatureDTO.completo(features.save(feature));
    }

    @Transactional
    public void excluir(Long id) {
        features.delete(exigir(id));
    }

    /**
     * Converte uma suíte e já guarda o resultado na aba do plano. A conversão
     * sozinha não servia de nada se o texto se perdesse ao fechar a janela.
     */
    @Transactional
    public GherkinFeatureDTO daSuite(Long suiteId, String provider, String nome, String autor) {
        SuiteDocService.Documento documento = docs.gherkin(suiteId, provider);

        GherkinFeature feature = new GherkinFeature();
        feature.setCreatedBy(autor);
        feature.setData(LocalDate.now());
        feature.setSuiteId(suiteId);
        feature.setName(nome == null || nome.isBlank() ? documento.title() : nome.trim());
        feature.setContent(documento.content());
        // O plano vem da própria suíte: procurá-lo pelo título do documento
        // acertaria por coincidência e erraria em dois planos de mesmo nome.
        suites.findById(suiteId).map(com.tester.entity.TestSuite::getTestPlan)
                .ifPresent(feature::setTestPlan);
        return GherkinFeatureDTO.completo(features.save(feature));
    }

    private void aplicar(GherkinFeature feature, GherkinFeatureDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new IllegalArgumentException("Dê um nome ao arquivo.");
        if (dto.getContent() == null || dto.getContent().isBlank())
            throw new IllegalArgumentException("O arquivo está vazio.");
        feature.setName(dto.getName().trim());
        feature.setContent(dto.getContent());
    }

    private GherkinFeature exigir(Long id) {
        return features.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo Gherkin não encontrado."));
    }
}
