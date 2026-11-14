package com.tester.entity;

import com.tester.enuns.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Um módulo dentro de uma regressão.
 *
 * {@code status} e {@code tester} nascem vazios de propósito e continuam assim
 * numa regressão clonada: a rodada nova começa com tudo por fazer e sem dono, e
 * é isso que separa o clone de uma cópia inútil do resultado anterior.
 */
@Entity
@Table(name = "tb_regression_item", schema = "operational")
public class RegressionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "system_module_id")
    private SystemModule systemModule;

    /** Título da demanda que trouxe este módulo, ou o que se escrever à mão. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String observation;

    /** O que precisa ser validado neste módulo — o "resultado esperado" da regressão. */
    @Column(columnDefinition = "TEXT")
    private String flow;

    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne
    @JoinColumn(name = "tester_id")
    private TesterQA tester;

    /** Jira de origem, quando o módulo veio de uma demanda concluída. */
    private String jiraKey;

    private Integer position;

    @ManyToOne
    @JoinColumn(name = "regression_id")
    private Regression regression;

    public RegressionItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SystemModule getSystemModule() {
        return systemModule;
    }

    public void setSystemModule(SystemModule systemModule) {
        this.systemModule = systemModule;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getFlow() {
        return flow;
    }

    public void setFlow(String flow) {
        this.flow = flow;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public TesterQA getTester() {
        return tester;
    }

    public void setTester(TesterQA tester) {
        this.tester = tester;
    }

    public String getJiraKey() {
        return jiraKey;
    }

    public void setJiraKey(String jiraKey) {
        this.jiraKey = jiraKey;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Regression getRegression() {
        return regression;
    }

    public void setRegression(Regression regression) {
        this.regression = regression;
    }
}
