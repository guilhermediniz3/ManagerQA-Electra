package com.tester.demand;
import java.util.List;import org.springframework.web.bind.annotation.*;import com.tester.integration.jira.service.JiraUserService;
@RestController @RequestMapping("/api") public class DemandController {
 private final DemandService service;private final JiraUserService users;private final SuiteCommentService comments;
 public DemandController(DemandService service,JiraUserService users,SuiteCommentService comments){this.service=service;this.users=users;this.comments=comments;}
 /** `statuses` vazio ou ausente envia a suíte inteira, como sempre foi. */
 public record Transition(String transitionId,Long suiteId,java.util.Set<com.tester.enuns.Status> statuses){}
 public record UserImport(String accountId,String kind){}
 @GetMapping("/demands") public List<DemandService.View> list(){return service.list();}
 @GetMapping("/demands/{key}") public DemandService.View get(@PathVariable String key){return service.get(key);}
 @PostMapping("/demands/{key}/sync") public DemandService.View sync(@PathVariable String key){return service.sync(key);}
 @PostMapping("/demands/{key}/transitions") public DemandService.View transition(@PathVariable String key,@RequestBody Transition input){return input.suiteId()==null?service.transition(key,input.transitionId()):service.transition(key,input.transitionId(),comments.document(key,input.suiteId(),input.statuses()));}
 @GetMapping("/demands/{key}/suite-comment") public SuiteCommentService.Preview preview(@PathVariable String key,@RequestParam Long suiteId,@RequestParam(required=false) java.util.Set<com.tester.enuns.Status> statuses){return comments.preview(key,suiteId,statuses);}
 @PatchMapping("/demands/{key}/local") public DemandService.View update(@PathVariable String key,@RequestBody DemandService.LocalInput input){return service.update(key,input);}
 @DeleteMapping("/demands/{key}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(@PathVariable String key){service.delete(key);}
 @PutMapping("/demands/{key}/ai-summary") public DemandService.View saveAiSummary(@PathVariable String key,@RequestBody DemandService.AiSummaryInput input){return service.saveAiSummary(key,input);}
 @PostMapping("/demands/{key}/test-plan") public DemandService.View plan(@PathVariable String key){return service.ensurePlan(key);}
 @PostMapping("/jira/users/import") public JiraUserService.Imported importUser(@RequestBody UserImport input){return users.importUser(input.accountId(),input.kind());}
}
