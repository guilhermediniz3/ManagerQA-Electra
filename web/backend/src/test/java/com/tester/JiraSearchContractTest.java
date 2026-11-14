package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.exception.GlobalExceptionHandler;
import com.tester.integration.IntegrationHttpException;
import com.tester.integration.jira.JiraController;
import com.tester.integration.jira.client.JiraClient;
import com.tester.integration.jira.service.JiraIssueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JiraSearchContractTest {
    private static final String PATH = "/rest/api/3/search/jql";
    private final JiraClient client = mock(JiraClient.class);
    private final JiraIssueService service = new JiraIssueService(client);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void defaultsToAnExplicitRecentPeriod(String query) {
        service.search(query, null);
        verify(client).get(PATH, Map.of("jql", "updated >= -30d ORDER BY updated DESC",
            "maxResults", "50", "fields", "summary,status,assignee,updated,project"));
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order by updated DESC", " ORDER BY key ", "ORDER\nBY created DESC"})
    void refusesOrderingWithoutARestrictionBeforeContactingJira(String query) {
        assertThatThrownBy(() -> service.search(query, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("filtro");
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"project = QA ORDER BY updated DESC", "sprint = 17", "summary ~ \"order by\""})
    void preservesUserRestrictionsAndPagination(String query) {
        service.search(query, "next-page-token");
        verify(client).get(PATH, Map.of("jql", query, "nextPageToken", "next-page-token",
            "maxResults", "50", "fields", "summary,status,assignee,updated,project"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void refusedJqlReturns400WithActionableMessageInsteadOf502() throws Exception {
        when(client.get(eq(PATH), anyMap())).thenThrow(new IntegrationHttpException("Jira", 400));
        var mvc = MockMvcBuilders.standaloneSetup(new JiraController(client, service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/jira/issues").param("jql", "invalid = value"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("consulta JQL")));
    }

    @Test
    void outageRemainsAnIntegrationError() throws Exception {
        when(client.get(eq(PATH), anyMap())).thenThrow(new IntegrationHttpException("Jira", 503));
        var mvc = MockMvcBuilders.standaloneSetup(new JiraController(client, service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/jira/issues").param("jql", "project = QA"))
            .andExpect(status().isBadGateway());
    }
}
