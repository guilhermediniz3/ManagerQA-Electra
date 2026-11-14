package com.tester.integration.drive;
import com.fasterxml.jackson.databind.*;
import com.tester.integration.config.*;
import org.springframework.stereotype.Service;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DriveService {
    static final String SCOPE="https://www.googleapis.com/auth/drive";
    private final IntegrationRepository repository;private final SecretCipher cipher;
    private final ObjectMapper mapper;private final DriveHttp http;
    private final Map<Long,Long> generations=new ConcurrentHashMap<>();
    private final Map<String,Pending> pending=new ConcurrentHashMap<>();
    public DriveService(IntegrationRepository repository,SecretCipher cipher,ObjectMapper mapper,DriveHttp http){
        this.repository=repository;this.cipher=cipher;this.mapper=mapper;this.http=http;
    }
    public record Config(String clientId,String clientSecret,String folder,String redirectUri,String refreshToken,String account) {}
    public record Input(String clientId,String clientSecret,String folder,String redirectUri) {}
    public record View(String clientId,boolean hasClientSecret,String folder,String redirectUri,boolean connected,boolean authorizing,String account) {}
    private record Pending(Long user,Config config,Instant expires,String verifier,long generation) {}
    private String key(Long user){return "google-drive-user-"+user;}
    private Config config(Long user){
        var row=repository.findById(key(user));
        if(row.isEmpty() || row.get().encryptedSecret==null)return new Config("","","","","","");
        try{return mapper.readValue(cipher.decrypt(row.get().encryptedSecret),Config.class);}
        catch(Exception e){throw new IllegalStateException("Não foi possível abrir a configuração do Drive. Verifique a chave mestra do servidor.");}
    }
    private void store(Long user,Config config){
        try{
            var row=repository.findById(key(user)).orElseGet(IntegrationSetting::new);row.provider=key(user);
            row.encryptedSecret=cipher.encrypt(mapper.writeValueAsString(config));repository.save(row);
        }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Não foi possível salvar a configuração do Drive.");}
    }
    public synchronized View view(Long user){
        var c=config(user);return new View(c.clientId(),!c.clientSecret().isBlank(),c.folder(),c.redirectUri(),!c.refreshToken().isBlank(),pending.values().stream().anyMatch(p->p.user().equals(user) && p.expires().isAfter(Instant.now())),c.account());
    }
    public synchronized View save(Long user,Input input){
        String id=clean(input.clientId()),secret=clean(input.clientSecret()),redirect=clean(input.redirectUri());
        if(!id.matches("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com"))throw new IllegalArgumentException("Informe o Client ID OAuth do Google.");
        validateRedirect(redirect);String folder=folderId(input.folder());var old=config(user);
        if(secret.isBlank() && id.equals(old.clientId()))secret=old.clientSecret();
        boolean same=id.equals(old.clientId()) && secret.equals(old.clientSecret()) && redirect.equals(old.redirectUri());
        store(user,new Config(id,secret,folder,redirect,same?old.refreshToken():"",same?old.account():""));
        generations.merge(user,1L,Long::sum);
        pending.values().removeIf(p->p.user().equals(user));
        return view(user);
    }
    static void validateRedirect(String value){
        try{
            var u=URI.create(value);boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(u.getHost());
            if(u.getHost()==null || !("https".equals(u.getScheme()) || (local && "http".equals(u.getScheme()))) || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || !"/api/drive/callback".equals(u.getPath()))throw new IllegalArgumentException();
        }catch(Exception e){throw new IllegalArgumentException("URL de retorno deve terminar em /api/drive/callback e usar HTTPS (ou HTTP local).");}
    }
    public static String folderId(String value){
        String text=clean(value);if(text.matches("[A-Za-z0-9_-]+"))return text;
        try{
            var u=URI.create(text);
            if("https".equals(u.getScheme()) && "drive.google.com".equals(u.getHost()) && u.getUserInfo()==null && u.getPort()==-1){
                var m=java.util.regex.Pattern.compile("^/drive/(u/[0-9]+/)?folders/([A-Za-z0-9_-]+)/?$").matcher(u.getPath());
                if(m.matches())return m.group(2);
            }
        }catch(Exception ignored){}
        throw new IllegalArgumentException("Cole o link da pasta do Google Drive ou o ID dela. Não use um caminho do disco.");
    }
    public synchronized String authorize(Long user){
        var c=config(user);
        if(c.clientId().isBlank() || c.folder().isBlank())throw new IllegalArgumentException("Salve as configurações do Google Drive primeiro.");
        pending.values().removeIf(p->p.expires().isBefore(Instant.now()) || p.user().equals(user));
        String state=random(),verifier=random();long generation=generations.merge(user,1L,Long::sum);
        pending.put(state,new Pending(user,c,Instant.now().plusSeconds(600),verifier,generation));
        try{
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return "https://accounts.google.com/o/oauth2/v2/auth?"+form(Map.of("client_id",c.clientId(),"redirect_uri",c.redirectUri(),"response_type","code","scope",SCOPE,"access_type","offline","prompt","consent select_account","state",state,"code_challenge",challenge,"code_challenge_method","S256"));
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public void callback(String state,String code,String error){
        Pending p=pending.remove(state==null?"":state);
        if(p==null || p.expires().isBefore(Instant.now()))throw new IllegalArgumentException("Conexão expirada ou inválida. Volte ao QA Manager e conecte novamente.");
        if(error!=null || code==null)throw new IllegalArgumentException("A conexão com o Google foi cancelada ou não foi autorizada.");
        var c=p.config();
        var data=new HashMap<>(Map.of("client_id",c.clientId(),"redirect_uri",c.redirectUri(),"grant_type","authorization_code","code",code,"code_verifier",p.verifier()));
        if(!c.clientSecret().isBlank())data.put("client_secret",c.clientSecret());
        var token=checked(http.request("POST","https://oauth2.googleapis.com/token",null,"application/x-www-form-urlencoded",form(data)),"Não foi possível conectar. Confira o Client ID, segredo e URL de retorno.");
        String refresh=token.path("refresh_token").asText();
        if(refresh.isBlank())throw new IllegalArgumentException("O Google não autorizou acesso contínuo. Conecte novamente e aceite as permissões.");
        if(!Arrays.asList(token.path("scope").asText(SCOPE).split(" ")).contains(SCOPE))throw new IllegalArgumentException("Autorize o acesso ao Drive para enviar à pasta configurada.");
        var about=checked(http.request("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token.path("access_token").asText(),null,null),"Não foi possível identificar a conta Google. Habilite a API Google Drive.");
        synchronized(this){
            if(!config(p.user()).equals(c) || generations.getOrDefault(p.user(),0L)!=p.generation())throw new IllegalArgumentException("A configuração mudou. Conecte novamente.");
            store(p.user(),new Config(c.clientId(),c.clientSecret(),c.folder(),c.redirectUri(),refresh,about.path("user").path("emailAddress").asText()));
        }
    }
    public synchronized void disconnect(Long user){
        var c=config(user);store(user,new Config(c.clientId(),c.clientSecret(),c.folder(),c.redirectUri(),"",""));
        generations.merge(user,1L,Long::sum);
        pending.values().removeIf(p->p.user().equals(user));
    }
    private String access(Config c){
        if(c.refreshToken().isBlank())throw new IllegalArgumentException("Conecte sua conta Google em Configurações / Integrações.");
        var data=new HashMap<>(Map.of("client_id",c.clientId(),"refresh_token",c.refreshToken(),"grant_type","refresh_token"));
        if(!c.clientSecret().isBlank())data.put("client_secret",c.clientSecret());
        var reply=checked(http.request("POST","https://oauth2.googleapis.com/token",null,"application/x-www-form-urlencoded",form(data)),"A autorização do Drive expirou ou foi revogada. Conecte novamente em Configurações.");
        String token=reply.path("access_token").asText();
        if(token.isBlank())throw new IllegalStateException("O Google não devolveu uma autorização de envio.");return token;
    }
    private JsonNode folder(Config c,String token){
        var folder=checked(http.request("GET","https://www.googleapis.com/drive/v3/files/"+c.folder()+"?supportsAllDrives=true&fields=id,name,mimeType,trashed,capabilities(canAddChildren)",token,null,null),"Pasta não encontrada ou sem acesso. Confira o link e a conta conectada.");
        if(folder.path("trashed").asBoolean() || !"application/vnd.google-apps.folder".equals(folder.path("mimeType").asText()) || !folder.path("capabilities").path("canAddChildren").asBoolean())
            throw new IllegalArgumentException("A conta conectada precisa ter permissão para adicionar arquivos nessa pasta.");
        return folder;
    }
    public String test(Long user){var c=config(user);return folder(c,access(c)).path("name").asText();}
    public String upload(Long user,Long caseId,Path file,String type){
        var c=config(user);String token=access(c);folder(c,token);
        var metadata=mapper.createObjectNode().put("name","QA-CT-"+caseId+"-"+Instant.now().toString().replace(':','-')+(type.equals("video/mp4")?".mp4":".webm")).put("mimeType",type);
        metadata.putArray("parents").add(c.folder());
        var session=http.request("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true&fields=id,webViewLink",token,"application/json",metadata.toString());
        checked(session,"Não foi possível iniciar o envio ao Drive. Verifique o espaço disponível e as permissões.");
        if(session.location()==null)throw new IllegalStateException("O Google não devolveu uma sessão de envio.");
        var result=checked(http.upload(session.location(),token,file,type),"O Drive não concluiu o envio. Confira o espaço disponível e tente novamente; você pode baixar a gravação.");
        String id=result.path("id").asText();
        if(!id.matches("[A-Za-z0-9_-]+"))throw new IllegalStateException("O Drive não devolveu o ID da gravação.");
        // Do not create public permissions: inherit the selected folder's sharing.
        String link=result.path("webViewLink").asText();
        return com.tester.service.VideoEvidenceUrl.valid(link)?link:"https://drive.google.com/file/d/"+id+"/view";
    }
    private static JsonNode checked(DriveHttp.Reply r,String message){if(r.status()<200 || r.status()>=300)throw new IllegalArgumentException(message);return r.body();}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String form(Map<String,String> data){return data.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
}
