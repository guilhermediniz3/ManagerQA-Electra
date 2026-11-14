package com.tester.integration.jira.service;
import com.tester.entity.*;import com.tester.repository.*;import com.tester.integration.jira.client.JiraClient;import java.util.Map;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class JiraUserService {
 private final JiraClient client;private final DeveloperRepository developers;private final TesterQARepository testers;
 public JiraUserService(JiraClient client,DeveloperRepository developers,TesterQARepository testers){this.client=client;this.developers=developers;this.testers=testers;}
 @Transactional public Developer developer(String id,String name){var d=developers.findByJiraAccountId(id).orElseGet(Developer::new);d.setJiraAccountId(id);d.setName(name);d.setActive(true);return developers.save(d);}
 @Transactional public TesterQA tester(String id,String name){var t=testers.findByJiraAccountId(id).orElseGet(TesterQA::new);t.setJiraAccountId(id);t.setName(name);t.setActive(true);return testers.save(t);}
 public record Imported(Long id,String name){}
 @Transactional public Imported importUser(String id,String kind){var u=client.get("/rest/api/3/user",Map.of("accountId",id));if(kind.equals("developer")){var d=developer(id,u.path("displayName").asText());return new Imported(d.getId(),d.getName());}if(kind.equals("tester")){var t=tester(id,u.path("displayName").asText());return new Imported(t.getId(),t.getName());}throw new IllegalArgumentException("Tipo de cadastro inválido.");}
}
