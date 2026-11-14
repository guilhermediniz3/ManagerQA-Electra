package com.tester.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tester.entity.DocCard;

public class DocCardDTO {

    private Long id;
    private String title;
    private String description;
    private String icon;
    private String content;
    private boolean active = true;
    private Integer sortOrder;
    private Long systemModuleId;
    private String systemModuleName;
    private Long suiteId;
    private String createdBy;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    public DocCardDTO() {
    }

    /**
     * Sem o conteúdo: a listagem mostra cards, e trazer o HTML inteiro de cada um
     * só para exibir título e descrição pesaria à toa.
     */
    public static DocCardDTO resumo(DocCard card) {
        DocCardDTO dto = new DocCardDTO();
        dto.id = card.getId();
        dto.title = card.getTitle();
        dto.description = card.getDescription();
        dto.icon = card.getIcon();
        dto.active = card.isActive();
        dto.sortOrder = card.getSortOrder();
        dto.suiteId = card.getSuiteId();
        dto.createdBy = card.getCreatedBy();
        dto.data = card.getData();
        if (card.getSystemModule() != null) {
            dto.systemModuleId = card.getSystemModule().getId();
            dto.systemModuleName = card.getSystemModule().getName();
        }
        return dto;
    }

    public static DocCardDTO completo(DocCard card) {
        DocCardDTO dto = resumo(card);
        dto.content = card.getContent();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
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
}
