package com.tester;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.demand.Demand;
import com.tester.demand.DemandRepository;
import com.tester.demand.DemandService;
import com.tester.entity.Developer;
import com.tester.entity.TestPlan;
import com.tester.entity.TesterQA;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import com.tester.integration.jira.service.JiraIssueService;
import com.tester.integration.jira.service.JiraUserService;
import com.tester.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DemandSynchronizationContractTest {
    private final DemandRepository repository = mock(DemandRepository.class);
    private final JiraIssueService jira = mock(JiraIssueService.class);
    private final IntegrationService settings = mock(IntegrationService.class);
    private final JiraUserService users = mock(JiraUserService.class);
    private final TestPlanRepository plans = mock(TestPlanRepository.class);
    private final TesterQARepository testers = mock(TesterQARepository.class);
    private final SystemModuleRepository modules = mock(SystemModuleRepository.class);
    private final TechnologyRepository technologies = mock(TechnologyRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final DemandService service = new DemandService(repository, jira, settings, users,
            plans, testers, modules, technologies, mapper);
    private final Demand demand = new Demand();

    @BeforeEach
    void configureExistingDemand() {
        demand.jiraKey = "QA-42";
        demand.assigneeAccountId = "previous-developer";
        demand.assigneeName = "Previous developer";
        demand.testPlan = new TestPlan();
        demand.testPlan.setId(9L);
        demand.testPlan.setJira("QA-42");
        demand.tester = new TesterQA(10L, "Local tester", true);
        demand.testPlan.setTester(demand.tester);
        demand.syncedAt = Instant.EPOCH;
        when(repository.findById("QA-42")).thenReturn(Optional.of(demand));
        when(repository.save(any(Demand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var setting = new IntegrationSetting();
        setting.testerField = "customfield_10001";
        when(settings.setting("jira")).thenReturn(setting);
    }

    @Test
    void syncPreservesLocalTesterOverrideAndRefreshesTheIndependentJiraAssignee() throws Exception {
        demand.testerOverride = true;
        var localTester = demand.tester;
        var developer = new Developer("Jira developer", true);
        developer.setJiraAccountId("jira-developer");
        when(users.developer("jira-developer", "Jira developer")).thenReturn(developer);
        when(jira.issue("QA-42")).thenReturn(issue("In progress", false));

        var result = service.sync("QA-42");

        assertThat(result.testerOverride()).isTrue();
        assertThat(result.testerId()).isEqualTo(10L);
        assertThat(demand.tester).isSameAs(localTester);
        assertThat(demand.testPlan.getTester()).isSameAs(localTester);
        assertThat(result.assigneeAccountId()).isEqualTo("jira-developer");
        assertThat(result.assigneeName()).isEqualTo("Jira developer");
        assertThat(demand.testPlan.getDeveloper()).isSameAs(developer);
        assertThat(result.syncedAt()).isAfter(Instant.EPOCH);
        verify(users, never()).tester(anyString(), anyString());
        verify(plans).save(demand.testPlan);
        verify(repository).save(demand);
    }

    @Test
    void syncUsesTheConfiguredTesterFieldAndNeverCopiesTheAssigneeIntoTester() throws Exception {
        var jiraTester = new TesterQA(20L, "Jira tester", true);
        jiraTester.setJiraAccountId("jira-tester");
        var developer = new Developer("Jira developer", true);
        when(users.tester("jira-tester", "Jira tester")).thenReturn(jiraTester);
        when(users.developer("jira-developer", "Jira developer")).thenReturn(developer);
        when(jira.issue("QA-42")).thenReturn(issue("In progress", false));

        var result = service.sync("QA-42");

        assertThat(result.testerOverride()).isFalse();
        assertThat(result.testerId()).isEqualTo(20L);
        assertThat(result.assigneeAccountId()).isEqualTo("jira-developer");
        assertThat(demand.testPlan.getTester()).isSameAs(jiraTester);
        assertThat(demand.testPlan.getDeveloper()).isSameAs(developer);
        verify(users).tester("jira-tester", "Jira tester");
        verify(users, never()).tester(eq("jira-developer"), anyString());
    }

    @Test
    void syncClearsADeletedJiraAssigneeWithoutClearingTheTesterOverride() throws Exception {
        demand.testerOverride = true;
        var localTester = demand.tester;
        demand.testPlan.setDeveloper(new Developer("Old developer", true));
        when(jira.issue("QA-42")).thenReturn(issue("In progress", true));

        var result = service.sync("QA-42");

        assertThat(result.assigneeAccountId()).isNull();
        assertThat(result.assigneeName()).isNull();
        assertThat(demand.testPlan.getDeveloper()).isNull();
        assertThat(demand.testPlan.getTester()).isSameAs(localTester);
        verifyNoInteractions(users);
    }

    @Test
    void transitionPersistsTheRefreshedJiraResponseLocally() throws Exception {
        demand.testerOverride = true;
        when(jira.transition("QA-42", "31")).thenReturn(issue("Done", false));

        var result = service.transition("QA-42", "31");

        assertThat(result.statusName()).isEqualTo("Done");
        assertThat(result.assigneeAccountId()).isEqualTo("jira-developer");
        assertThat(result.testerId()).isEqualTo(10L);
        assertThat(result.syncedAt()).isAfter(Instant.EPOCH);
        var sequence = inOrder(repository, jira);
        sequence.verify(repository).findById("QA-42");
        sequence.verify(jira).transition("QA-42", "31");
        sequence.verify(repository).findById("QA-42");
        sequence.verify(repository).save(demand);
        verify(jira, never()).issue(anyString());
    }

    @Test
    void localTesterEditDoesNotChangeOrSendAJiraAssignee() {
        var newTester = new TesterQA(30L, "Another local tester", true);
        when(testers.findById(30L)).thenReturn(Optional.of(newTester));

        var result = service.update("QA-42",
                new DemandService.LocalInput(30L, true, null, null, "CALL-99", null));

        assertThat(result.testerId()).isEqualTo(30L);
        assertThat(result.testerOverride()).isTrue();
        assertThat(result.assigneeAccountId()).isEqualTo("previous-developer");
        assertThat(result.assigneeName()).isEqualTo("Previous developer");
        assertThat(result.callNumber()).isEqualTo("CALL-99");
        verifyNoInteractions(jira);
    }

    @Test
    void disablingTheOverrideResynchronizesTheTesterFromJira() throws Exception {
        demand.testerOverride = true;
        var jiraTester = new TesterQA(20L, "Jira tester", true);
        when(users.tester("jira-tester", "Jira tester")).thenReturn(jiraTester);
        when(jira.issue("QA-42")).thenReturn(issue("In progress", false));

        var result = service.update("QA-42",
                new DemandService.LocalInput(null, false, null, null, "CALL-99", null));

        assertThat(result.testerOverride()).isFalse();
        assertThat(result.testerId()).isEqualTo(20L);
        assertThat(result.assigneeAccountId()).isEqualTo("jira-developer");
        verify(jira).issue("QA-42");
    }

    private JsonNode issue(String status, boolean unassigned) throws Exception {
        return mapper.readTree("""
                {
                  "key": "QA-42",
                  "fields": {
                    "summary": "Check login",
                    "description": {
                      "type": "doc",
                      "content": [{"type":"paragraph","content":[{"type":"text","text":"Login acceptance criteria"}]}]
                    },
                    "status": {"id":"3","name":"%s"},
                    "project": {"key":"QA"},
                    "assignee": %s,
                    "customfield_10001": {"accountId":"jira-tester","displayName":"Jira tester"},
                    "updated": "2026-09-09T12:00:00.000+0000"
                  }
                }
                """.formatted(status, unassigned ? "null" :
                "{\"accountId\":\"jira-developer\",\"displayName\":\"Jira developer\"}"));
    }
}
