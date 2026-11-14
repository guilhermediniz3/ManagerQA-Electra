package com.tester.dto;

import com.tester.entity.RegressionItem;
import com.tester.enuns.Status;

public class RegressionItemDTO {

    private Long id;
    private Long systemModuleId;
    private String systemModuleName;
    private String description;
    private String observation;
    private String flow;
    private Status status;
    private Long testerId;
    private String testerName;
    private String jiraKey;
    private Integer position;

    public RegressionItemDTO() {
    }

    public RegressionItemDTO(RegressionItem item) {
        this.id = item.getId();
        this.description = item.getDescription();
        this.observation = item.getObservation();
        this.flow = item.getFlow();
        this.status = item.getStatus();
        this.jiraKey = item.getJiraKey();
        this.position = item.getPosition();
        if (item.getSystemModule() != null) {
            this.systemModuleId = item.getSystemModule().getId();
            this.systemModuleName = item.getSystemModule().getName();
        }
        if (item.getTester() != null) {
            this.testerId = item.getTester().getId();
            this.testerName = item.getTester().getName();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemModuleId() {
        return systemModuleId;
    }

    public void setSystemModuleId(Long systemModuleId) {
        this.systemModuleId = systemModuleId;
    }

    public String getSystemModuleName() {
        return systemModuleName;
    }

    public void setSystemModuleName(String systemModuleName) {
        this.systemModuleName = systemModuleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getFlow() {
        return flow;
    }

    public void setFlow(String flow) {
        this.flow = flow;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getTesterId() {
        return testerId;
    }

    public void setTesterId(Long testerId) {
        this.testerId = testerId;
    }

    public String getTesterName() {
        return testerName;
    }

    public void setTesterName(String testerName) {
        this.testerName = testerName;
    }

    public String getJiraKey() {
        return jiraKey;
    }

    public void setJiraKey(String jiraKey) {
        this.jiraKey = jiraKey;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}
