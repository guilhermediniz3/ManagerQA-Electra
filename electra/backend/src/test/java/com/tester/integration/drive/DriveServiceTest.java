package com.tester.integration.drive;
import com.fasterxml.jackson.databind.*;
import com.tester.integration.config.*;
import com.tester.service.VideoEvidenceUrl;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DriveServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final IntegrationRepository repo=mock(IntegrationRepository.class);
    final DriveHttp http=mock(DriveHttp.class);
    final Map<String,IntegrationSetting> rows=new HashMap<>();
    final DriveService service;
    DriveServiceTest(){
        when(repo.findById(anyString())).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i->{IntegrationSetting r=i.getArgument(0);rows.put(r.provider,r);return r;});
        service=new DriveService(repo,new SecretCipher(Base64.getEncoder().encodeToString(new byte[32])),mapper,http);
    }
    DriveService.Input input(String folder){return new DriveService.Input("test.apps.googleusercontent.com","fixture-secret",folder,"http://127.0.0.1:18081/api/drive/callback");}
    DriveHttp.Reply reply(String json,int status) throws Exception {return new DriveHttp.Reply(status,mapper.readTree(json),null);}
    String state(String url){return Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(p->p.startsWith("state=")).map(p->URLDecoder.decode(p.substring(6),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
    void stubOAuth() throws Exception {
        when(http.request(eq("POST"),eq("https://oauth2.googleapis.com/token"),isNull(),anyString(),anyString()))
            .thenReturn(reply("{\"access_token\":\"fixture-access\",\"refresh_token\":\"fixture-refresh\",\"scope\":\"https://www.googleapis.com/auth/drive\"}",200));
        when(http.request(eq("GET"),contains("/about?"),anyString(),isNull(),isNull()))
            .thenReturn(reply("{\"user\":{\"emailAddress\":\"qa@example.test\"}}",200));
    }
    void connect() throws Exception {service.save(7L,input("folder_123"));stubOAuth();service.callback(state(service.authorize(7L)),"code",null);}
    @Test void configIsEncryptedPrivateToUserAndNeverReturnsTokens() throws Exception {
        service.save(7L,input("https://drive.google.com/drive/u/0/folders/folder_123?usp=sharing"));
        assertThat(service.view(7L).folder()).isEqualTo("folder_123");
        assertThat(service.view(8L).clientId()).isEmpty();
        assertThat(rows.get("google-drive-user-7").encryptedSecret).doesNotContain("fixture-secret","folder_123");
        assertThat(mapper.writeValueAsString(service.view(7L))).doesNotContain("fixture-secret","refreshToken");
    }
    @Test void stateIsRandomSingleUseAndPkceIsPresent() throws Exception {
        service.save(7L,input("folder_123"));stubOAuth();
        String first=service.authorize(7L),second=service.authorize(7L);
        assertThat(first).contains("code_challenge_method=S256","access_type=offline");
        assertThat(state(first)).isNotEqualTo(state(second));
        assertThatThrownBy(()->service.callback(state(first),"code",null)).hasMessageContaining("inválida");
        service.callback(state(second),"code",null);
        assertThat(service.view(7L).connected()).isTrue();
        assertThat(service.view(7L).account()).isEqualTo("qa@example.test");
        assertThat(service.view(8L).connected()).isFalse();
        assertThatThrownBy(()->service.callback(state(second),"code",null)).hasMessageContaining("inválida");
        verify(http).request(eq("POST"),eq("https://oauth2.googleapis.com/token"),isNull(),anyString(),contains("code_verifier="));
    }
    @Test void cancelAndDisconnectInvalidatePendingAuthorization() throws Exception {
        service.save(7L,input("folder_123"));
        String a=state(service.authorize(7L));
        assertThatThrownBy(()->service.callback(a,null,"access_denied")).hasMessageContaining("cancelada");
        String b=state(service.authorize(7L));service.disconnect(7L);
        assertThatThrownBy(()->service.callback(b,"code",null)).hasMessageContaining("inválida");
        verifyNoInteractions(http);
    }
    @Test void disconnectDuringTokenExchangeCannotReconnectAccount() throws Exception {
        service.save(7L,input("folder_123"));stubOAuth();
        when(http.request(eq("POST"),eq("https://oauth2.googleapis.com/token"),isNull(),anyString(),anyString())).thenAnswer(i->{
            service.disconnect(7L);return reply("{\"access_token\":\"a\",\"refresh_token\":\"r\"}",200);
        });
        String state=state(service.authorize(7L));
        assertThatThrownBy(()->service.callback(state,"code",null)).hasMessageContaining("configuração mudou");
        assertThat(service.view(7L).connected()).isFalse();
    }
    @Test void uploadUsesFolderAndSharedDriveSupportWithoutMakingVideoPublic() throws Exception {
        connect();clearInvocations(http);
        when(http.request(eq("GET"),contains("/files/folder_123?"),anyString(),isNull(),isNull()))
            .thenReturn(reply("{\"name\":\"Evidências\",\"mimeType\":\"application/vnd.google-apps.folder\",\"capabilities\":{\"canAddChildren\":true}}",200));
        when(http.request(eq("POST"),contains("uploadType=resumable"),anyString(),eq("application/json"),anyString()))
            .thenReturn(new DriveHttp.Reply(200,mapper.createObjectNode(),"https://www.googleapis.com/upload/drive/v3/files?upload_id=fixture"));
        when(http.upload(anyString(),anyString(),any(),eq("video/webm")))
            .thenReturn(reply("{\"id\":\"video_123\",\"webViewLink\":\"https://drive.google.com/file/d/video_123/view?resourcekey=key\"}",200));
        assertThat(service.upload(7L,42L,Path.of("fixture.webm"),"video/webm"))
            .isEqualTo("https://drive.google.com/file/d/video_123/view?resourcekey=key");
        var metadata=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(http).request(eq("POST"),contains("supportsAllDrives=true"),anyString(),eq("application/json"),metadata.capture());
        assertThat(mapper.readTree(metadata.getValue()).path("parents").get(0).asText()).isEqualTo("folder_123");
        assertThat(metadata.getValue()).contains("QA-CT-42").doesNotContain("permissions","anyone");
        verify(http,never()).request(anyString(),contains("/permissions"),any(),any(),any());
    }
    @Test void unavailableFolderRejectsUploadBeforeCreatingVideo() throws Exception {
        connect();
        when(http.request(eq("GET"),contains("/files/folder_123?"),anyString(),isNull(),isNull())).thenReturn(reply("{}",404));
        assertThatThrownBy(()->service.upload(7L,42L,Path.of("fixture.webm"),"video/webm")).hasMessageContaining("Pasta não encontrada");
        verify(http,never()).upload(anyString(),anyString(),any(),anyString());
        verify(http,never()).request(eq("POST"),contains("uploadType="),any(),any(),any());
    }
    @Test void malformedUrlsAndNonGoogleUploadLocationAreRejected(){
        for(String value:List.of("https://drive.google.com.evil.test/drive/folders/abc","C:\\Videos","https://drive.google.com/file/d/abc/view"))
            assertThatThrownBy(()->DriveService.folderId(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DriveHttp(mapper).upload("https://evil.test/upload/drive/file","token",Path.of("x"),"video/webm")).hasMessageContaining("inválido");
        assertThatThrownBy(()->DriveService.validateRedirect("http://external.example/api/drive/callback")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsBothProvidersAndRejectsLookalikes(){
        for(String value:List.of("https://vimeo.com/123/abcd?h=private","https://player.vimeo.com/video/123?h=abc","https://vimeo.com/manage/videos/123/abc","https://drive.google.com/file/d/video_123/view?usp=sharing&resourcekey=abc","https://drive.google.com/open?id=abc"))
            assertThat(VideoEvidenceUrl.valid(value)).as(value).isTrue();
        for(String value:List.of("javascript:alert(1)","http://vimeo.com/123","https://vimeo.com.evil.test/123","https://drive.google.com@evil.test/file/d/id/view","https://drive.google.com/drive/folders/folder","https://drive.google.com/file/d/a/view/extra"))
            assertThat(VideoEvidenceUrl.valid(value)).as(value).isFalse();
    }
}
