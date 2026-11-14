package com.tester.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tester.entity.Regression;

public class RegressionDTO {

    private Long id;
    private String name;

    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate data;

    private String observation;
    private String createdBy;
    private List<RegressionItemDTO> items = new ArrayList<>();

    /** Totais para a listagem, que não carrega os itens. */
    private int totalItems;
    private int totalConcluidos;
    private int totalPendentes;

    public RegressionDTO() {
    }

    public static RegressionDTO resumo(Regression regression) {
        RegressionDTO dto = new RegressionDTO();
        dto.id = regression.getId();
        dto.name = regression.getName();
        dto.data = regression.getData();
        dto.observation = regression.getObservation();
        dto.createdBy = regression.getCreatedBy();
        dto.totalItems = regression.getItems().size();
        dto.totalConcluidos = (int) regression.getItems().stream()
                .filter(item -> item.getStatus() == com.tester.enuns.Status.CONCLUIDA).count();
        dto.totalPendentes = dto.totalItems - dto.totalConcluidos;
        return dto;
    }

    public static RegressionDTO completo(Regression regression) {
        RegressionDTO dto = resumo(regression);
        dto.items = regression.getItems().stream().map(RegressionItemDTO::new).toList();
        return dto;
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

    public List<RegressionItemDTO> getItems() {
        return items;
    }

    public void setItems(List<RegressionItemDTO> items) {
        this.items = items;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalConcluidos() {
        return totalConcluidos;
    }

    public void setTotalConcluidos(int totalConcluidos) {
        this.totalConcluidos = totalConcluidos;
    }

    public int getTotalPendentes() {
        return totalPendentes;
    }

    public void setTotalPendentes(int totalPendentes) {
        this.totalPendentes = totalPendentes;
    }
}
