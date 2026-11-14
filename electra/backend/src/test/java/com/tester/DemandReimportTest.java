package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.demand.DemandRepository;
import com.tester.demand.DemandService;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import com.tester.integration.jira.service.JiraIssueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:demand_reimport;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class DemandReimportTest {
    @Autowired DemandService service;
    @Autowired DemandRepository repository;
    @Autowired ObjectMapper mapper;
    @MockitoBean JiraIssueService jira;
    @MockitoBean IntegrationService settings;

    @Test
    void repeatedImportsUseTheCanonicalJiraKeyAndUpdateOneDatabaseRow() throws Exception {
        when(settings.setting("jira")).thenReturn(new IntegrationSetting());
        var first = mapper.readTree("""
            {"key":"QA-42","fields":{"summary":"Original","status":{"id":"1","name":"Aberto"},"project":{"key":"QA"}}}
            """);
        var updated = mapper.readTree("""
            {"key":"QA-42","fields":{"summary":"Atualizada","status":{"id":"2","name":"Concluído"},"project":{"key":"QA"}}}
            """);
        when(jira.issue("QA-42")).thenReturn(first, updated);
        when(jira.issue("qa-42")).thenReturn(updated);

        service.sync("QA-42");
        var local = repository.findById("QA-42").orElseThrow();
        local.callNumber = "LOCAL-123";
        local.testerOverride = true;
        repository.saveAndFlush(local);

        service.sync("QA-42");
        service.sync("qa-42");

        assertThat(repository.count()).isEqualTo(1);
        var saved = repository.findById("QA-42").orElseThrow();
        assertThat(saved.summary).isEqualTo("Atualizada");
        assertThat(saved.statusId).isEqualTo("2");
        assertThat(saved.callNumber).isEqualTo("LOCAL-123");
        assertThat(saved.testerOverride).isTrue();
        verify(jira, times(2)).issue("QA-42");
        verify(jira).issue("qa-42");
        verifyNoMoreInteractions(jira);
    }
}
