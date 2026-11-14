package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * O botão que dispara — "HML", "PROD" — dentro de um container.
 *
 * Guarda o que o documento pediu: título, ambiente e script. O script é o
 * comando que vai ser executado, e fica aqui em vez de ser deduzido da marcação
 * como o painel antigo fazia; o sistema e a versão são opcionais e servem para
 * saber onde rodar e o que informar à pipeline.
 */
@Entity
@Table(name = "tb_run_card", schema = "operational")
public class RunCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_container_id")
    private RunContainer container;

    private String title;

    @ManyToOne
    @JoinColumn(name = "run_environment_id")
    private RunEnvironment environment;

    @ManyToOne
    @JoinColumn(name = "run_system_id")
    private RunSystem system;

    @ManyToOne
    @JoinColumn(name = "run_version_id")
    private RunVersion version;

    @ManyToOne
    @JoinColumn(name = "run_tag_id")
    private RunTag tag;

    /** O comando, como seria digitado no terminal. */
    @Column(columnDefinition = "TEXT")
    private String script;

    private boolean active = true;

    private Integer position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunContainer getContainer() { return container; }
    public void setContainer(RunContainer container) { this.container = container; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public RunEnvironment getEnvironment() { return environment; }
    public void setEnvironment(RunEnvironment environment) { this.environment = environment; }

    public RunSystem getSystem() { return system; }
    public void setSystem(RunSystem system) { this.system = system; }

    public RunVersion getVersion() { return version; }
    public void setVersion(RunVersion version) { this.version = version; }

    public RunTag getTag() { return tag; }
    public void setTag(RunTag tag) { this.tag = tag; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
