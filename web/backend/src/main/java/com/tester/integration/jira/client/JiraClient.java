package com.tester.integration.jira.client;
import java.net.*;import java.net.http.*;import java.time.Duration;import java.util.*;import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.*;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.IntegrationException;
import org.springframework.stereotype.Component;
@Component public class JiraClient {
 private final IntegrationService settings;private final ObjectMapper mapper;
 private final com.tester.integration.JsonHttpTransport transport;
 public JiraClient(IntegrationService settings,ObjectMapper mapper,com.tester.integration.JsonHttpTransport transport){this.settings=settings;this.mapper=mapper;this.transport=transport;}
 public JsonNode get(String path,Map<String,String> query){
  if(!path.matches("/rest/(api/3|agile/1.0)/[A-Za-z0-9_/-]+"))throw new IllegalArgumentException("Recurso Jira inválido.");
  String suffix=query.isEmpty()?"":"?"+query.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
  return send(path+suffix,null);
 }
 public static String issueKey(String key){if(key==null || !key.matches("[A-Z][A-Z0-9_]*-[0-9]+"))throw new IllegalArgumentException("Jira Key inválida.");return key;}
 public void transition(String key,String id){
  issueKey(key);if(id==null || !id.matches("[0-9]+"))throw new IllegalArgumentException("Transição inválida.");
  send("/rest/api/3/issue/"+key+"/transitions",Map.of("transition",Map.of("id",id)));
 }
 /**
  * Publica o comentário pelo recurso próprio de comentários, e não embutido no
  * `update` da transição. Uma transição só aceita `comment` quando a tela dela
  * inclui esse campo; quando não inclui, o Jira leva a mudança de status adiante
  * e o texto se perde pelo caminho. Pelo recurso dedicado o comentário não
  * depende da tela — e a resposta traz o id do que foi criado, então dá para
  * afirmar que foi publicado em vez de supor.
  */
 public String comment(String key,JsonNode body){
  issueKey(key);
  var criado=body.has("qaWiki")
   ? send("/rest/api/2/issue/"+key+"/comment",Map.of("body",body.path("qaWiki").asText()))
   : send("/rest/api/3/issue/"+key+"/comment",Map.of("body",body));
  String id=criado.path("id").asText(null);
  if(id==null || id.isBlank())throw new IntegrationException("O Jira aceitou a requisição mas não devolveu o comentário criado. Confira a demanda antes de reenviar.");
  return id;
 }
 /**
  * Cria uma demanda.
  *
  * Existe para o RunManager: transformar uma falha de teste em demanda, com o
  * log e o vídeo dentro, é o motivo pelo qual o painel foi trazido para cá. O
  * restante do sistema continua só consultando, comentando e transicionando —
  * criar demanda a partir de uma tela de cadastro de testes seria outra coisa,
  * e não foi pedida.
  */
 public String create(JsonNode fields){
  var criado=send("/rest/api/3/issue",Map.of("fields",fields));
  String key=criado.path("key").asText(null);
  if(key==null || key.isBlank())throw new IntegrationException("O Jira aceitou a requisição mas não devolveu a demanda criada.");
  return key;
 }
 public void attachment(String key,String name,String mime,byte[] bytes) { uploadAttachment(key,name,mime,bytes); }
 private String uploadAttachment(String key,String name,String mime,byte[] bytes) {
  issueKey(key);
  // Unique filenames also make retries safe after a partially successful send.
  for(var item:get("/rest/api/3/issue/"+key,Map.of("fields","attachment")).path("fields").path("attachment"))
   if(name.equals(item.path("filename").asText()))return item.path("id").asText();
  var cfg=settings.setting("jira");
  String auth=Base64.getEncoder().encodeToString((cfg.username+":"+settings.secret("jira")).getBytes(StandardCharsets.UTF_8));
  String boundary="qa-"+UUID.randomUUID();
  String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n";
  byte[] prefix=head.getBytes(StandardCharsets.UTF_8),suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
  try {
   var request=HttpRequest.newBuilder(URI.create(cfg.baseUrl+"/rest/api/3/issue/"+key+"/attachments"))
    .timeout(Duration.ofMinutes(35)).header("Authorization","Basic "+auth).header("X-Atlassian-Token","no-check")
    .header("Content-Type","multipart/form-data; boundary="+boundary)
    .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(suffix))).build();
   var response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build().send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200 || response.statusCode()>=300)throw new IntegrationException("O Jira recusou o anexo (HTTP "+response.statusCode()+"). Confira o limite de anexos e suas permissões. O status não foi alterado.");
   var result=mapper.readTree(response.body());
   if(!result.isArray() || result.isEmpty() || result.get(0).path("id").asText().isBlank())throw new IntegrationException("O Jira não confirmou o anexo. Confira a demanda antes de reenviar.");
   return result.get(0).path("id").asText();
  } catch(IntegrationException e){throw e;} catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IntegrationException("Falha ao enviar anexo ao Jira. Confira a demanda antes de reenviar.");}
 }
 public String attachmentMedia(String key,String name,String mime,byte[] bytes) {
  String id=uploadAttachment(key,name,mime,bytes);
  if(!id.matches("[0-9]+"))throw new IntegrationException("O Jira não retornou um identificador de anexo válido.");
  var cfg=settings.setting("jira");
  String auth=Base64.getEncoder().encodeToString((cfg.username+":"+settings.secret("jira")).getBytes(StandardCharsets.UTF_8));
  try {
   var request=HttpRequest.newBuilder(URI.create(cfg.baseUrl+"/rest/api/3/attachment/content/"+id+"?redirect=true"))
    .timeout(Duration.ofSeconds(30)).header("Authorization","Basic "+auth).GET().build();
   var response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build().send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(var body=response.body()) {
    if(response.statusCode()!=303 && response.statusCode()!=302 && response.statusCode()!=307)throw new IntegrationException("O Jira anexou o arquivo, mas não forneceu a mídia para exibi-lo no corpo. O comentário não foi publicado; confira as permissões do anexo antes de tentar novamente.");
    var location=URI.create(response.headers().firstValue("Location").orElse(""));
    var match=java.util.regex.Pattern.compile("/file/([a-fA-F0-9-]{36})(?:/|$)").matcher(java.util.Objects.toString(location.getPath(),""));
    if(match.find())return UUID.fromString(match.group(1)).toString();
    throw new IntegrationException("O Jira anexou o arquivo, mas não retornou um identificador de mídia reconhecido. O comentário não foi publicado.");
   }
  }catch(IntegrationException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IntegrationException("Não foi possível preparar a mídia no corpo do comentário Jira. Os anexos já enviados serão reaproveitados na próxima tentativa.");}
 }
 private JsonNode send(String path,Object payload){
  var s=settings.setting("jira");String auth=Base64.getEncoder().encodeToString((s.username+":"+settings.secret("jira")).getBytes(StandardCharsets.UTF_8));
  return transport.request(s.baseUrl+path,Map.of("Authorization","Basic "+auth),payload,30000,"Jira");
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
