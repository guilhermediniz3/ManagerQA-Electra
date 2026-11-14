package com.tester.runmanager.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * Uma execução disparada pelo painel.
 *
 * Guarda cópia do que foi executado — título, script, ambiente, sistema — em
 * vez de só apontar para o card. O card muda com o tempo, e um histórico que
 * lesse o cadastro atual passaria a mentir sobre o que rodou naquele dia.
 *
 * O log fica na própria linha porque é o que a tela do terminal lê, e porque
 * separá-lo numa tabela de linhas só faria sentido se houvesse busca dentro do
 * log — que ninguém pediu.
 */
@Entity
@Table(name = "tb_run_execution", schema = "operational")
public class RunExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo quando a execução veio da seleção livre de casos. */
    @ManyToOne
    @JoinColumn(name = "run_card_id")
    private RunCard card;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String script;

    @Column(name = "environment_key")
    private String environmentKey;

    @Column(name = "system_key")
    private String systemKey;

    /** Onde rodou: "local" ou "pipeline". */
    private String engine;

    /** RUNNING, SUCCESS, FAILED, CANCELLED. */
    private String state;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Endereço do build quando quem executou foi a pipeline. */
    @Column(name = "build_url")
    private String buildUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String log;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunCard getCard() { return card; }
    public void setCard(RunCard card) { this.card = card; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }

    public String getSystemKey() { return systemKey; }
    public void setSystemKey(String systemKey) { this.systemKey = systemKey; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getBuildUrl() { return buildUrl; }
    public void setBuildUrl(String buildUrl) { this.buildUrl = buildUrl; }

    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }
}
