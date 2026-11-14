package com.tester.integration;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.Map;import com.fasterxml.jackson.databind.*;import org.springframework.stereotype.Component;
/** Blocking, bounded HTTP transport. Redirects never receive credentials. */
@Component public class JsonHttpTransport {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(JsonHttpTransport.class);
 private final ObjectMapper mapper;public JsonHttpTransport(ObjectMapper mapper){this.mapper=mapper;}
 public JsonNode request(String url,Map<String,String> headers,Object body,int timeout,String provider){HttpURLConnection connection=null;try{
  connection=(HttpURLConnection)URI.create(url).toURL().openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(timeout);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");for(var entry:headers.entrySet())connection.setRequestProperty(entry.getKey(),entry.getValue());
  if(body==null)connection.setRequestMethod("GET");else{connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);try(var output=connection.getOutputStream()){output.write(mapper.writeValueAsBytes(body));}}
  int status=connection.getResponseCode();if(status<200||status>=300)throw new IntegrationHttpException(provider,status,reason(connection));
  try(var input=connection.getInputStream()){byte[] bytes=input.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)throw new IntegrationException("Resposta do provider excedeu o limite.");return bytes.length==0?mapper.createObjectNode():mapper.readTree(bytes);}
 }catch(IntegrationException e){throw e;}catch(Exception e){log.warn("Falha ao falar com {} em {}: {}",provider,url,e.toString());if(e instanceof java.net.SocketTimeoutException)throw new IntegrationTimeoutException(provider+" demorou demais para responder.");throw new IntegrationException(provider+" indisponível ou resposta inválida.");}finally{if(connection!=null)connection.disconnect();}}

 /**
  * Resume por que o provider recusou, lendo só os campos que ele reserva para
  * explicar a recusa ao usuário. Sem isso, uma recusa de campo — "comment não
  * está na tela desta transição", por exemplo — chegava como um "HTTP 400" mudo
  * e não havia como saber o que corrigir. O corpo bruto continua fora da
  * mensagem, e qualquer falha na leitura simplesmente não acrescenta nada.
  */
 private String reason(HttpURLConnection connection){
  try(var stream=connection.getErrorStream()){
   if(stream==null)return null;
   var json=mapper.readTree(stream.readNBytes(64*1024));
   var partes=new java.util.LinkedHashSet<String>();
   for(var message:json.path("errorMessages"))if(message.isTextual())partes.add(message.asText());
   var fields=json.path("errors");
   if(fields.isObject())fields.fields().forEachRemaining(field->partes.add(field.getKey()+": "+field.getValue().asText()));
   if(partes.isEmpty())return null;
   String texto=String.join(" ",partes);
   return texto.length()>500?texto.substring(0,500)+"…":texto;
  }catch(Exception ignored){return null;}
 }
}
