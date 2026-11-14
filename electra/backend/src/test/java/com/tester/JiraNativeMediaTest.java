package com.tester;
import com.fasterxml.jackson.databind.ObjectMapper;import com.tester.integration.JsonHttpTransport;import com.tester.integration.config.*;import com.tester.integration.jira.client.JiraClient;
import com.sun.net.httpserver.HttpServer;import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
class JiraNativeMediaTest {
    @Test void uploadsOnceResolvesMediaIdWithoutFollowingSignedRedirectAndPostsAdf() throws Exception {
        var mapper=new ObjectMapper();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var uploads=new AtomicInteger();var comment=new AtomicReference<String>();
        String uuid="12345678-1234-4123-8123-123456789abc";
        server.createContext("/",exchange->{
            String route=exchange.getRequestURI().getPath();String body="{}";
            if(route.endsWith("/attachments")){assertThat(exchange.getRequestHeaders().getFirst("X-Atlassian-Token")).isEqualTo("no-check");exchange.getRequestBody().readAllBytes();uploads.incrementAndGet();body="[{\"id\":\"100\"}]";}
            else if(route.contains("/attachment/content/")){exchange.getResponseHeaders().set("Location","https://api.media.atlassian.com/file/"+uuid+"/binary?token=fixture");exchange.sendResponseHeaders(303,-1);exchange.close();return;}
            else if(route.endsWith("/comment")){comment.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body="{\"id\":\"200\"}";}
            else body=uploads.get()==0?"{\"fields\":{\"attachment\":[]}}":"{\"fields\":{\"attachment\":[{\"id\":\"100\",\"filename\":\"video.webm\"}]}}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try{
            var settings=mock(IntegrationService.class);var cfg=new IntegrationSetting();cfg.baseUrl="http://127.0.0.1:"+server.getAddress().getPort();cfg.username="fixture";when(settings.setting("jira")).thenReturn(cfg);when(settings.secret("jira")).thenReturn("fixture");
            var client=new JiraClient(settings,mapper,new JsonHttpTransport(mapper));
            assertThat(client.attachmentMedia("QA-42","video.webm","video/webm",new byte[]{1,2})).isEqualTo(uuid);assertThat(client.attachmentMedia("QA-42","video.webm","video/webm",new byte[]{1,2})).isEqualTo(uuid);assertThat(uploads.get()).isEqualTo(1);
            var doc=mapper.readTree("{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"mediaSingle\",\"attrs\":{\"layout\":\"align-start\"},\"content\":[{\"type\":\"media\",\"attrs\":{\"id\":\""+uuid+"\",\"type\":\"file\",\"collection\":\"\",\"width\":240,\"height\":135}}]}]}");
            client.comment("QA-42",doc);assertThat(mapper.readTree(comment.get()).path("body")).isEqualTo(doc);
        }finally{server.stop(0);}
    }
}
