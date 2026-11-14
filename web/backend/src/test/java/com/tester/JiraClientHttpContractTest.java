package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.io.*;
import java.util.concurrent.*;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import com.tester.integration.jira.client.JiraClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JiraClientHttpContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsOnlyReadRequestsAndTheSpecificTransitionPost() throws Exception {
        var requests = new CopyOnWriteArrayList<ReceivedRequest>();
        var server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.setSoTimeout(10000);
        var worker = Executors.newSingleThreadExecutor();
        var received = worker.submit(() -> {
            for (int i=0;i<4;i++) try (var socket=server.accept()) {
                socket.setSoTimeout(10000);
                var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String[] request=reader.readLine().split(" ",3);
                int length=0; String auth=null; String line;
                while((line=reader.readLine())!=null && !line.isEmpty()) {
                    int colon=line.indexOf(':'); String name=line.substring(0,colon);
                    String value=line.substring(colon+1).trim();
                    if(name.equalsIgnoreCase("Content-Length"))length=Integer.parseInt(value);
                    if(name.equalsIgnoreCase("Authorization"))auth=value;
                }
                char[] body=new char[length]; int offset=0;
                while(offset<length){int n=reader.read(body,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}
                var uri=java.net.URI.create(request[1]);
                requests.add(new ReceivedRequest(request[0],uri.getPath(),uri.getRawQuery(),new String(body),auth));
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            }
            return true;
        });
        try {
            var settings = mock(IntegrationService.class);
            var setting = new IntegrationSetting();
            setting.baseUrl = "http://127.0.0.1:" + server.getLocalPort();
            setting.username = "fixture@example.test";
            when(settings.setting("jira")).thenReturn(setting);
            when(settings.secret("jira")).thenReturn("fixture-secret");
            var client = new JiraClient(settings, mapper, new JsonHttpTransport(mapper));

            client.get("/rest/api/3/issue/QA-42", Map.of("fields", "*all"));
            client.get("/rest/api/3/search/jql", Map.of("jql", "project = QA & text ~ \"login\""));
            client.get("/rest/agile/1.0/board", Map.of());
            client.transition("QA-42", "31");

            assertThat(received.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasSize(4);
            assertThat(requests.subList(0, 3)).extracting(ReceivedRequest::method)
                    .containsOnly("GET");
            assertThat(requests.subList(0, 3)).extracting(ReceivedRequest::body).containsOnly("");
            assertThat(requests.get(1).query())
                    .isEqualTo("jql=project+%3D+QA+%26+text+%7E+%22login%22");
            assertThat(requests.get(2).query()).isNull();
            var transition = requests.get(3);
            assertThat(transition.method()).isEqualTo("POST");
            assertThat(transition.path()).isEqualTo("/rest/api/3/issue/QA-42/transitions");
            assertThat(mapper.readTree(transition.body()))
                    .isEqualTo(mapper.readTree("{\"transition\":{\"id\":\"31\"}}"));
            String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                    "fixture@example.test:fixture-secret".getBytes(StandardCharsets.UTF_8));
            assertThat(requests).extracting(ReceivedRequest::authorization).containsOnly(expectedAuth);
        } finally {
            server.close();
            worker.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://external.example/rest/api/3/issue",
            "/rest/api/3/issue/QA-1?override=true",
            "/rest/api/3/../issue/QA-1",
            "/rest/api/2/issue/QA-1",
            "/rest/api/3/issue/QA-1#fragment"
    })
    void rejectsUnexpectedReadPathsBeforeCredentialsOrTransport(String path) {
        var settings = mock(IntegrationService.class);
        var transport = mock(JsonHttpTransport.class);
        var client = new JiraClient(settings, mapper, transport);

        assertThatThrownBy(() -> client.get(path, Map.of())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(settings, transport);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"qa-42", "QA-42/transitions", "../QA-42", "QA-42?x=1"})
    void rejectsInvalidIssueKeysBeforeAnyWrite(String key) {
        var settings = mock(IntegrationService.class);
        var transport = mock(JsonHttpTransport.class);
        var client = new JiraClient(settings, mapper, transport);

        assertThatThrownBy(() -> client.transition(key, "31")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(settings, transport);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"31/../../issue", "-1", "31?x=1", "Done"})
    void rejectsInvalidTransitionIdsBeforeAnyWrite(String id) {
        var settings = mock(IntegrationService.class);
        var transport = mock(JsonHttpTransport.class);
        var client = new JiraClient(settings, mapper, transport);

        assertThatThrownBy(() -> client.transition("QA-42", id)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(settings, transport);
    }

    private record ReceivedRequest(String method, String path, String query, String body,
                                   String authorization) {}
}
