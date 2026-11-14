package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Caso de teste cadastrado à mão, com o script que o executa.
 *
 * Existe porque nem todo caso vive no projeto de automação: há verificação que
 * ainda é manual, e há script que mora fora do padrão que a leitura reconhece.
 * Ele aparece na mesma lista dos casos lidos do projeto, marcado como manual,
 * para que a seleção possa misturar os dois numa execução só.
 */
@Entity
@Table(name = "tb_run_manual_case", schema = "operational")
public class RunManualCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_system_id")
    private RunSystem system;

    /** Módulo a que pertence, no mesmo formato da leitura: "v2/dashboard". */
    @Column(name = "module_key")
    private String moduleKey;

    @Column(name = "module_name")
    private String moduleName;

    private String version;

    private String code;

    private String title;

    /** O que rodar para executar este caso. */
    @Column(columnDefinition = "TEXT")
    private String script;

    /** Separadas por vírgula, sem arroba. */
    private String tags;

    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunSystem getSystem() { return system; }
    public void setSystem(RunSystem system) { this.system = system; }

    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
