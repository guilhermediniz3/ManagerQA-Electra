package com.tester.integration.config;
import java.util.*;import org.springframework.web.bind.annotation.*;import com.tester.integration.ai.AiService;import com.tester.integration.jira.client.JiraClient;
@RestController @RequestMapping("/api/integrations") public class IntegrationController {
 private final IntegrationService service;private final JiraClient jira;private final AiService ai;
 public IntegrationController(IntegrationService service,JiraClient jira,AiService ai){this.service=service;this.jira=jira;this.ai=ai;}
 @GetMapping public List<IntegrationService.View> list(){return service.list();}
 @PutMapping("/{provider}") public IntegrationService.View save(@PathVariable String provider,@RequestBody IntegrationService.Input input){return service.save(provider,input);}
 @PostMapping("/{provider}/test") public IntegrationService.View test(@PathVariable String provider){try{if(provider.equals("jira"))jira.get("/rest/api/3/myself",Map.of());else ai.verify(provider);return service.status(provider,true);}catch(RuntimeException e){service.status(provider,false);throw e;}}
}
