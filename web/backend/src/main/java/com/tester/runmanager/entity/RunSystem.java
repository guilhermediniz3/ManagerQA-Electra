package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Um sistema que tem automação — Ultracar, PortoCar, o que vier.
 *
 * Antes isso era constante no código do painel: acrescentar um sistema exigia
 * editar e publicar. Aqui é cadastro, e é o que permite que alguém de fora do
 * time de desenvolvimento ligue um projeto novo ao painel.
 *
 * O endereço do projeto é guardado como repositório, e não como pasta: a versão
 * web roda num servidor que não enxerga o disco de quem usa, então um caminho
 * local só valeria para quem estivesse sentado naquela máquina. O caminho local
 * existe como atalho para o aplicativo instalado, onde ele de fato funciona.
 */
@Entity
@Table(name = "tb_run_system", schema = "operational")
public class RunSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Como aparece na tela: "Ultracar". */
    private String name;

    /** Como entra no comando: "ultracar". */
    @Column(name = "system_key")
    private String key;

    /** workspace/repositório, no formato que a API de código espera. */
    private String repository;

    /** Branch padrão para disparo e leitura. */
    private String branch;

    /** Caminho local, só usado pelo aplicativo instalado. */
    @Column(name = "local_path")
    private String localPath;

    /**
     * Como montar o comando de uma seleção de casos.
     *
     * O trecho {{casos}} é trocado pelos alvos escolhidos. Em branco, vale o
     * padrão do Playwright — que é o que este projeto usa —, e quem roda
     * Cypress ou Selenium escreve aqui o comando da sua ferramenta sem
     * precisar de versão nova do aplicativo.
     */
    @Column(name = "selection_script", columnDefinition = "TEXT")
    private String selectionScript;

    /**
     * Como o comando escolhe a versão.
     *
     * {{versao}} é trocado pelo identificador da versão. Em branco, vale
     * {@code --project={{versao}}}, que é como o Playwright faz. Um projeto
     * Cypress ou Selenium escreve aqui a forma dele — ou deixa a palavra
     * "nenhum" para não acrescentar nada e resolver tudo pelo script do card.
     */
    @Column(name = "version_flag")
    private String versionFlag;

    /**
     * Variáveis que valem para toda execução deste sistema, uma por linha.
     *
     * Servem para o que é do projeto e não do ambiente — um caminho, uma
     * credencial de serviço. O ambiente é aplicado depois e vence em caso de
     * repetição, porque ele é o mais específico dos dois.
     */
    @Column(columnDefinition = "TEXT")
    private String variables;

    private boolean active = true;

    private Integer position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getSelectionScript() { return selectionScript; }
    public void setSelectionScript(String selectionScript) { this.selectionScript = selectionScript; }

    public String getVersionFlag() { return versionFlag; }
    public void setVersionFlag(String versionFlag) { this.versionFlag = versionFlag; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
