package com.tester.runmanager.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * O bloco da tela de disparo — "Smoke V2", "Regressão PortoCar".
 *
 * No painel antigo esses blocos eram o resultado de uma conta feita no código:
 * marcação × sistema × ambiente. Aqui cada bloco é cadastrado, e é isso que
 * permite um atalho que não cabe naquela conta — uma suíte específica, um
 * roteiro de release, o que o time precisar.
 */
@Entity
@Table(name = "tb_run_container", schema = "operational")
public class RunContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean active = true;

    private Integer position;

    /**
     * Ordem importa: quem monta o painel espera reencontrar os cards na
     * sequência em que os colocou, porque ela costuma ser a ordem de execução.
     */
    @OneToMany(mappedBy = "container", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<RunCard> cards = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public List<RunCard> getCards() { return cards; }
    public void setCards(List<RunCard> cards) { this.cards = cards; }
}
