package com.tester.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Caso de teste dentro de um template.
 *
 * Tem os campos de definição de um caso — código, cenário e resultado esperado.
 * Resultado obtido, evidência e status ficam de fora de propósito: são
 * preenchidos ao executar, e num molde só voltariam como sujeira para limpar a
 * cada importação. É a mesma escolha que o clone de suíte já fazia ao zerar o
 * status e descartar a evidência.
 */
@Entity
@Table(name = "tb_template_case", schema = "operational")
public class TemplateCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long codeCase;

    @Column(columnDefinition = "TEXT")
    private String scenario;

    @Column(columnDefinition = "TEXT")
    private String expectedResult;

    /** Posição no roteiro; preserva a ordem em que o template foi montado. */
    private Integer position;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private SuiteTemplate template;

    public TemplateCase() {
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

    public SuiteTemplate getTemplate() {
        return template;
    }

    public void setTemplate(SuiteTemplate template) {
        this.template = template;
    }
}
