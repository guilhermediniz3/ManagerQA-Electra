package com.tester.integration.jira.service;
import java.util.*;import com.fasterxml.jackson.databind.JsonNode;import com.tester.integration.jira.client.JiraClient;import org.springframework.stereotype.Service;
@Service public class JiraIssueService {
 private final JiraClient client;public JiraIssueService(JiraClient client){this.client=client;}
 public JsonNode issue(String key){return client.get("/rest/api/3/issue/"+JiraClient.issueKey(key),Map.of("fields","*all"));}

 public static final String DEFAULT_JQL = "updated >= -30d ORDER BY updated DESC";

 public JsonNode search(String jql, String cursor) {
  String query = jql == null || jql.isBlank() ? DEFAULT_JQL : jql.strip();
  if (query.matches("(?is)^order\\s+by\\b.*")) {
   throw new IllegalArgumentException("A consulta precisa de um filtro antes de ORDER BY. Use updated >= -30d ORDER BY updated DESC ou filtre por projeto, sprint ou chave.");
  }
  var parameters = new HashMap<String, String>();
  parameters.put("jql", query);
  parameters.put("maxResults", "50");
  parameters.put("fields", "summary,status,assignee,updated,project");
  if (cursor != null && !cursor.isBlank()) parameters.put("nextPageToken", cursor);
  try {
   return client.get("/rest/api/3/search/jql", parameters);
  } catch (com.tester.integration.IntegrationHttpException error) {
   if (error.getStatus() == 400) {
    throw new IllegalArgumentException("O Jira recusou a consulta JQL. Confira os campos, operadores e valores e inclua um filtro de projeto, sprint, chave ou período.", error);
   }
   throw error;
  }
 }
 public JsonNode transitions(String key){return client.get("/rest/api/3/issue/"+JiraClient.issueKey(key)+"/transitions",Map.of("expand","transitions.fields"));}
 public JsonNode transition(String key,String id){return transition(key,id,null);}
 /**
  * Publica o comentário ANTES de mover o status, quando há conteúdo a enviar.
  * A ordem importa: se o comentário falhar, a demanda continua onde estava e a
  * pessoa pode corrigir e repetir sem que o Jira já tenha registrado um avanço
  * de status sem o relatório que o justifica — que é justamente o que acontecia
  * quando o comentário ia embutido na transição e se perdia em silêncio.
  */
 public JsonNode transition(String key,String id,JsonNode comment){
  boolean allowed=false;
  for(var t:transitions(key).path("transitions"))if(t.path("id").asText().equals(id))allowed=true;
  if(!allowed)throw new IllegalArgumentException("Transição não disponível para esta issue.");
  String commentId=comment==null?null:client.comment(key,comment);
  try{client.transition(key,id);}catch(com.tester.integration.IntegrationException error){
   if(commentId==null)throw error;
   throw new com.tester.integration.IntegrationException("O comentário com o conteúdo da suíte foi publicado, mas a mudança de status falhou: "+error.getMessage()+" Altere o status no Jira sem reenviar o comentário.");
  }
  try{return issue(key);}catch(com.tester.integration.IntegrationException error){
   throw new com.tester.integration.IntegrationException("O Jira confirmou a alteração"+(comment==null?"":" e o comentário")+", mas a atualização local falhou. Sincronize a demanda para conferir; não repita o envio.");
  }
 }
}
