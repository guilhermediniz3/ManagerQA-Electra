package com.tester.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tester.entity.SuiteTemplate;

public class SuiteTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private Long systemModuleId;
    private String systemModuleName;
    private boolean active = true;
    private String createdBy;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    private List<TemplateCaseDTO> cases = new ArrayList<>();

    /** Quantos casos o template tem — a listagem mostra isso sem carregar todos. */
    private int totalCases;

    public SuiteTemplateDTO() {
    }

    /** Sem os casos: para a listagem, que só precisa do resumo. */
    public static SuiteTemplateDTO resumo(SuiteTemplate template) {
        SuiteTemplateDTO dto = new SuiteTemplateDTO();
        dto.id = template.getId();
        dto.name = template.getName();
        dto.description = template.getDescription();
        dto.active = template.isActive();
        dto.createdBy = template.getCreatedBy();
        dto.data = template.getData();
        if (template.getSystemModule() != null) {
            dto.systemModuleId = template.getSystemModule().getId();
            dto.systemModuleName = template.getSystemModule().getName();
        }
        dto.totalCases = template.getCases().size();
        return dto;
    }

    /** Com os casos: para abrir o template e para importar. */
    public static SuiteTemplateDTO completo(SuiteTemplate template) {
        SuiteTemplateDTO dto = resumo(template);
        dto.cases = template.getCases().stream().map(TemplateCaseDTO::new).toList();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getSystemModuleId() {
        return systemModuleId;
    }

    public void setSystemModuleId(Long systemModuleId) {
        this.systemModuleId = systemModuleId;
    }

    public String getSystemModuleName() {
        return systemModuleName;
    }

    public void setSystemModuleName(String systemModuleName) {
        this.systemModuleName = systemModuleName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public List<TemplateCaseDTO> getCases() {
        return cases;
    }

    public void setCases(List<TemplateCaseDTO> cases) {
        this.cases = cases;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }
}
