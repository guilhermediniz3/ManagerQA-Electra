package com.tester.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tester.entity.GherkinFeature;

public class GherkinFeatureDTO {

    private Long id;
    private String name;
    private String content;
    private Long testPlanId;
    private Long suiteId;
    private String createdBy;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    /** Quantos cenários o arquivo tem — a listagem mostra sem abrir o conteúdo. */
    private int totalCenarios;

    public GherkinFeatureDTO() {
    }

    /** Sem o conteúdo: a listagem só precisa saber o que existe e o tamanho. */
    public static GherkinFeatureDTO resumo(GherkinFeature feature) {
        GherkinFeatureDTO dto = new GherkinFeatureDTO();
        dto.id = feature.getId();
        dto.name = feature.getName();
        dto.suiteId = feature.getSuiteId();
        dto.createdBy = feature.getCreatedBy();
        dto.data = feature.getData();
        if (feature.getTestPlan() != null) dto.testPlanId = feature.getTestPlan().getId();
        dto.totalCenarios = contarCenarios(feature.getContent());
        return dto;
    }

    public static GherkinFeatureDTO completo(GherkinFeature feature) {
        GherkinFeatureDTO dto = resumo(feature);
        dto.content = feature.getContent();
        return dto;
    }

    /** Conta por linha iniciada em "Cenário"/"Cenario"/"Scenario". */
    static int contarCenarios(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) return 0;
        int total = 0;
        for (String linha : conteudo.split("\\R")) {
            String limpa = linha.strip().toLowerCase();
            if (limpa.startsWith("cenário:") || limpa.startsWith("cenario:")
                    || limpa.startsWith("scenario:") || limpa.startsWith("esquema do cenário:")
                    || limpa.startsWith("scenario outline:")) {
                total++;
            }
        }
        return total;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getTestPlanId() { return testPlanId; }
    public void setTestPlanId(Long testPlanId) { this.testPlanId = testPlanId; }
    public Long getSuiteId() { return suiteId; }
    public void setSuiteId(Long suiteId) { this.suiteId = suiteId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public int getTotalCenarios() { return totalCenarios; }
    public void setTotalCenarios(int totalCenarios) { this.totalCenarios = totalCenarios; }
}
