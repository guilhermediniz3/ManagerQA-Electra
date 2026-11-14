package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Uma versão do sistema — V2, V3.
 *
 * Fica pendurada no sistema porque versão só existe dentro de um: o Ultracar
 * tem V2 e V3, e outro sistema pode não ter versão nenhuma. Sem esse vínculo, a
 * tela ofereceria a V3 do Ultracar para disparar no PortoCar.
 */
@Entity
@Table(name = "tb_run_version", schema = "operational")
public class RunVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_system_id")
    private RunSystem system;

    private String name;

    @Column(name = "version_key")
    private String key;

    private boolean active = true;

    private Integer position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunSystem getSystem() { return system; }
    public void setSystem(RunSystem system) { this.system = system; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
