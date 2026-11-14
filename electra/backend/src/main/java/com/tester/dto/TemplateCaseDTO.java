package com.tester.dto;

import com.tester.entity.TemplateCase;

public class TemplateCaseDTO {

    private Long id;
    private Long codeCase;
    private String scenario;
    private String expectedResult;
    private Integer position;

    public TemplateCaseDTO() {
    }

    public TemplateCaseDTO(TemplateCase entity) {
        this.id = entity.getId();
        this.codeCase = entity.getCodeCase();
        this.scenario = entity.getScenario();
        this.expectedResult = entity.getExpectedResult();
        this.position = entity.getPosition();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCodeCase() {
        return codeCase;
    }

    public void setCodeCase(Long codeCase) {
        this.codeCase = codeCase;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}
