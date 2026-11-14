package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.IntegrationException;
import com.tester.integration.jira.client.JiraClient;
import com.tester.integration.jira.service.JiraIssueService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JiraTransitionWorkflowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JiraClient client = mock(JiraClient.class);
    private final JiraIssueService service = new JiraIssueService(client);

    @Test
    void checksCurrentAvailabilityThenTransitionsAndFetchesTheUpdatedIssue() throws Exception {
        var available = mapper.readTree("{\"transitions\":[{\"id\":\"31\",\"name\":\"Done\"}]}");
        var updated = mapper.readTree("{\"key\":\"QA-42\",\"fields\":{\"status\":{\"id\":\"3\",\"name\":\"Done\"}}}");
        when(client.get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields"))).thenReturn(available);
        when(client.get("/rest/api/3/issue/QA-42", Map.of("fields", "*all"))).thenReturn(updated);

        assertThat(service.transition("QA-42", "31")).isSameAs(updated);

        var sequence = inOrder(client);
        sequence.verify(client).get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields"));
        sequence.verify(client).transition("QA-42", "31");
        sequence.verify(client).get("/rest/api/3/issue/QA-42", Map.of("fields", "*all"));
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void refusesUnavailableTransitionsWithoutSendingAnyWrite() throws Exception {
        when(client.get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields")))
                .thenReturn(mapper.readTree("{\"transitions\":[{\"id\":\"21\"}]}"));

        assertThatThrownBy(() -> service.transition("QA-42", "31"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não disponível");

        verify(client).get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void doesNotReportARefreshedIssueWhenJiraRejectsTheWrite() throws Exception {
        when(client.get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields")))
                .thenReturn(mapper.readTree("{\"transitions\":[{\"id\":\"31\"}]}"));
        doThrow(new IntegrationException("Jira respondeu HTTP 403."))
                .when(client).transition("QA-42", "31");

        assertThatThrownBy(() -> service.transition("QA-42", "31"))
                .isInstanceOf(IntegrationException.class).hasMessageContaining("403");

        verify(client).get("/rest/api/3/issue/QA-42/transitions",
                Map.of("expand", "transitions.fields"));
        verify(client).transition("QA-42", "31");
        verifyNoMoreInteractions(client);
    }
}
