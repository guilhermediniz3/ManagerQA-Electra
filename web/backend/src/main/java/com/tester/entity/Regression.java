package com.tester.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Uma rodada de regressão: a lista de módulos que precisam ser revalidados numa
 * data, com quem valida cada um e em que pé está.
 *
 * Os módulos costumam vir das demandas que fecharam desde a última rodada — é o
 * que o botão de buscar aprovados faz —, mas nada impede acrescentar outros à
 * mão, porque nem tudo que precisa de regressão passou por uma demanda.
 */
@Entity
@Table(name = "tb_regression", schema = "operational")
public class Regression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    @Column(columnDefinition = "TEXT")
    private String observation;

    private String createdBy;

    /**
     * Lista ordenada: a sequência dos módulos é a ordem em que a regressão vai
     * ser executada, e quem montou espera reencontrá-la assim.
     */
    @OneToMany(mappedBy = "regression", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<RegressionItem> items = new ArrayList<>();

    public Regression() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public List<RegressionItem> getItems() {
        return items;
    }

    public void setItems(List<RegressionItem> items) {
        this.items = items;
    }
}
