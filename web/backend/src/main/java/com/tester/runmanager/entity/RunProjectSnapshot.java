package com.tester.runmanager.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * A última leitura do projeto de automação de um sistema.
 *
 * Guardar o resultado evita varrer o disco a cada abertura de tela — a leitura
 * de um projeto grande leva segundos — e, principalmente, deixa a tela
 * funcionar quando o projeto não está à mão: na versão web, ou num computador
 * que não tem a pasta. A data da leitura fica visível justamente para que
 * ninguém confunda o que está em tela com o que está no disco agora.
 */
@Entity
@Table(name = "tb_run_project_snapshot", schema = "operational")
public class RunProjectSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_system_id")
    private RunSystem system;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "scanned_by")
    private String scannedBy;

    @Column(name = "project_root")
    private String projectRoot;

    @Column(name = "total_modules")
    private int totalModules;

    @Column(name = "total_tests")
    private int totalTests;

    /** A leitura inteira, como veio do scanner. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunSystem getSystem() { return system; }
    public void setSystem(RunSystem system) { this.system = system; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }

    public String getScannedBy() { return scannedBy; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }

    public int getTotalModules() { return totalModules; }
    public void setTotalModules(int totalModules) { this.totalModules = totalModules; }

    public int getTotalTests() { return totalTests; }
    public void setTotalTests(int totalTests) { this.totalTests = totalTests; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
