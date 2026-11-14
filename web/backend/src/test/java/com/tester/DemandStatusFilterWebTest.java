package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.demand.*;
import com.tester.enuns.Status;
import com.tester.integration.jira.service.JiraUserService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre a fronteira HTTP do filtro por status: a tela manda os status como texto
 * (query na prévia, JSON no envio) e é o Spring que os converte em enum. Os
 * testes de serviço recebem o Set pronto e não veriam uma conversão errada aqui.
 */
class DemandStatusFilterWebTest {
    final DemandService service = mock(DemandService.class);
    final SuiteCommentService comments = mock(SuiteCommentService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new DemandController(service, mock(JiraUserService.class), comments)).build();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Status>> captor() { return ArgumentCaptor.forClass(Set.class); }

    @Test void previewQueryStringBecomesStatusSet() throws Exception {
        when(comments.preview(anyString(), anyLong(), anySet()))
            .thenReturn(new SuiteCommentService.Preview("t", List.of(), 1));

        mvc.perform(get("/api/demands/QA-42/suite-comment")
                .param("suiteId", "9").param("statuses", "RETORNO,CONCLUIDA"))
           .andExpect(status().isOk());

        var filtro = captor();
        verify(comments).preview(eq("QA-42"), eq(9L), filtro.capture());
        assertThat(filtro.getValue()).containsExactlyInAnyOrder(Status.RETORNO, Status.CONCLUIDA);
    }

    /** Sem o parâmetro, o serviço recebe null e trata como "a suíte inteira". */
    @Test void previewWithoutStatusesKeepsTheWholeSuite() throws Exception {
        when(comments.preview(anyString(), anyLong(), any()))
            .thenReturn(new SuiteCommentService.Preview("t", List.of(), 3));

        mvc.perform(get("/api/demands/QA-42/suite-comment").param("suiteId", "9"))
           .andExpect(status().isOk());

        verify(comments).preview("QA-42", 9L, null);
    }

    @Test void transitionForwardsSuiteAndStatusesToTheComment() throws Exception {
        when(comments.document(anyString(), anyLong(), anySet())).thenReturn(mapper.createObjectNode());

        mvc.perform(post("/api/demands/QA-42/transitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transitionId\":\"31\",\"suiteId\":9,\"statuses\":[\"RETORNO\"]}"))
           .andExpect(status().isOk());

        var filtro = captor();
        verify(comments).document(eq("QA-42"), eq(9L), filtro.capture());
        assertThat(filtro.getValue()).containsExactly(Status.RETORNO);
        verify(service).transition(eq("QA-42"), eq("31"), any());
    }

    /** Sem suíte escolhida nada de comentário é montado — só o status muda. */
    @Test void transitionWithoutSuiteNeverBuildsAComment() throws Exception {
        mvc.perform(post("/api/demands/QA-42/transitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transitionId\":\"31\"}"))
           .andExpect(status().isOk());

        verify(service).transition("QA-42", "31");
        verifyNoInteractions(comments);
    }
}
