package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Um ambiente de execução — homologação, produção.
 *
 * O campo que importa aqui não é o nome: é o {@code production}. Ele é o que faz
 * o botão nascer vermelho e pedir confirmação antes de disparar, e por isso é
 * escolha de quem cadastra, e não dedução a partir do nome — um ambiente
 * chamado "pré" pode muito bem estar apontando para dados reais.
 */
@Entity
@Table(name = "tb_run_environment", schema = "operational")
public class RunEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "environment_key")
    private String key;

    /** Exige confirmação e aparece em vermelho. */
    private boolean production;

    /**
     * O que muda de verdade entre um ambiente e outro.
     *
     * Uma variável por linha, no formato CHAVE=valor. É isto que troca a URL:
     * o projeto de automação lê o ambiente do próprio ambiente do processo, e
     * sem passar nada o teste roda sempre no mesmo lugar — o rótulo do botão
     * diria "PROD" e a execução continuaria em homologação.
     *
     * Em branco, vale ENV=&lt;identificador&gt;, que é a convenção do projeto de
     * testes deste time.
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

    public boolean isProduction() { return production; }
    public void setProduction(boolean production) { this.production = production; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
