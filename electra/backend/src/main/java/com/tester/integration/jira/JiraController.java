package com.tester.integration.jira;
import java.util.*;import com.fasterxml.jackson.databind.JsonNode;import org.springframework.web.bind.annotation.*;import com.tester.integration.jira.client.JiraClient;import com.tester.integration.jira.service.JiraIssueService;
@RestController @RequestMapping("/api/jira") public class JiraController {
 private final JiraClient client;private final JiraIssueService issues;
 public JiraController(JiraClient client,JiraIssueService issues){this.client=client;this.issues=issues;}
 @GetMapping("/issues") public JsonNode search(@RequestParam(required=false)String jql,@RequestParam(required=false)String cursor){return issues.search(jql,cursor);}
 @GetMapping("/issues/{key}") public JsonNode issue(@PathVariable String key){return issues.issue(key);}
 @GetMapping("/issues/{key}/transitions") public JsonNode transitions(@PathVariable String key){return issues.transitions(key);}
 @GetMapping("/fields") public JsonNode fields(){return client.get("/rest/api/3/field",Map.of());}
 @GetMapping("/statuses") public JsonNode statuses(){return client.get("/rest/api/3/status",Map.of());}
 @GetMapping("/projects") public JsonNode projects(@RequestParam(defaultValue="0")int startAt){return client.get("/rest/api/3/project/search",Map.of("startAt",String.valueOf(startAt)));}
 @GetMapping("/boards") public JsonNode boards(@RequestParam(defaultValue="0")int startAt){return client.get("/rest/agile/1.0/board",Map.of("startAt",String.valueOf(startAt)));}
 @GetMapping("/boards/{id}/sprints") public JsonNode sprints(@PathVariable long id,@RequestParam(defaultValue="0")int startAt){return client.get("/rest/agile/1.0/board/"+id+"/sprint",Map.of("startAt",String.valueOf(startAt)));}
 @GetMapping("/users") public JsonNode users(@RequestParam String query,@RequestParam(defaultValue="0")int startAt){return client.get("/rest/api/3/user/search",Map.of("query",query,"startAt",String.valueOf(startAt)));}
}
