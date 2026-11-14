package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Uma marcação de suíte — smoke, regression, journey, permission.
 *
 * As quatro que o projeto usa hoje estavam escritas no código do painel. Como
 * cadastro, marcação nova entra sem publicar versão, e a leitura do projeto
 * pode sugerir as que encontrar nos arquivos sem apagar as que alguém criou à
 * mão.
 */
@Entity
@Table(name = "tb_run_tag", schema = "operational")
public class RunTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Sem o arroba: "smoke". */
    @Column(name = "tag_key")
    private String key;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean active = true;

    private Integer position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
