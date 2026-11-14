package com.tester.runmanager.entity;

import jakarta.persistence.*;

/**
 * Um caso que falhou numa execução.
 *
 * Nasce da leitura do relatório do Playwright e é o que alimenta a aba de
 * falhas e a criação de demanda no Jira. Guarda o caminho do vídeo e do
 * screenshot como o relatório os entregou; o endereço do Vimeo só aparece
 * depois que alguém manda enviar, porque subir vídeo de toda falha de toda
 * execução encheria a conta sem que ninguém tivesse pedido.
 */
@Entity
@Table(name = "tb_run_failure", schema = "operational")
public class RunFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_execution_id")
    private RunExecution execution;

    private String title;

    @Column(name = "full_name", columnDefinition = "TEXT")
    private String fullName;

    private String file;

    @Column(name = "module_key")
    private String moduleKey;

    private String version;

    /** REGRA_NEGOCIO, STATUS_CODE, LOCATOR, INFRA ou OUTRO. */
    private String bucket;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "video_path")
    private String videoPath;

    @Column(name = "screenshot_path")
    private String screenshotPath;

    /** Preenchido quando o vídeo é enviado para o Vimeo. */
    @Column(name = "vimeo_url")
    private String vimeoUrl;

    /** Preenchido quando a demanda é criada. */
    @Column(name = "jira_key")
    private String jiraKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunExecution getExecution() { return execution; }
    public void setExecution(RunExecution execution) { this.execution = execution; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }

    public String getVimeoUrl() { return vimeoUrl; }
    public void setVimeoUrl(String vimeoUrl) { this.vimeoUrl = vimeoUrl; }

    public String getJiraKey() { return jiraKey; }
    public void setJiraKey(String jiraKey) { this.jiraKey = jiraKey; }
}
