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
 * Molde reutilizável de casos de teste.
 *
 * Um template guarda o roteiro — cenário e resultado esperado —, não a execução.
 * Ele existe para que uma suíte nova comece com os casos que já se repetem de
 * demanda em demanda, em vez de serem redigitados. Quem executa preenche
 * resultado obtido, evidência e status na suíte, que é onde esses campos vivem.
 */
@Entity
@Table(name = "tb_suite_template", schema = "operational")
public class SuiteTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Módulo a que o template se refere, quando faz sentido restringir. */
    @jakarta.persistence.ManyToOne
    @jakarta.persistence.JoinColumn(name = "system_module_id")
    private SystemModule systemModule;

    private boolean active = true;

    private String createdBy;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    /**
     * Lista, e não conjunto: a ordem dos casos é parte do roteiro, e quem monta
     * o template espera reencontrá-los na mesma sequência ao importar.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<TemplateCase> cases = new ArrayList<>();

    public SuiteTemplate() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SystemModule getSystemModule() {
        return systemModule;
    }

    public void setSystemModule(SystemModule systemModule) {
        this.systemModule = systemModule;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public List<TemplateCase> getCases() {
        return cases;
    }

    public void setCases(List<TemplateCase> cases) {
        this.cases = cases;
    }
}
