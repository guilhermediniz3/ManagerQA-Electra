package com.tester.demand;
import java.util.*;import java.time.*;import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.entity.*;import com.tester.repository.*;import com.tester.enuns.Status;
import com.tester.integration.config.IntegrationService;import com.tester.integration.jira.service.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class DemandService {
 private final DemandRepository repository;private final JiraIssueService jira;private final IntegrationService settings;private final JiraUserService users;
 private final TestPlanRepository plans;private final TesterQARepository testers;private final SystemModuleRepository modules;private final TechnologyRepository technologies;private final ObjectMapper mapper;
 public DemandService(DemandRepository repository,JiraIssueService jira,IntegrationService settings,JiraUserService users,TestPlanRepository plans,TesterQARepository testers,SystemModuleRepository modules,TechnologyRepository technologies,ObjectMapper mapper){this.repository=repository;this.jira=jira;this.settings=settings;this.users=users;this.plans=plans;this.testers=testers;this.modules=modules;this.technologies=technologies;this.mapper=mapper;}
 public record View(String jiraKey,String summary,String description,String statusId,String statusName,String assigneeName,String assigneeAccountId,String projectKey,String squad,String sprint,String jiraUpdated,Instant syncedAt,Long testerId,String testerName,boolean testerOverride,Long moduleId,String moduleName,Long technologyId,String technologyName,String callNumber,Long testPlanId,AiSummary aiSummary){}
 public record AiSummary(String provider,String summary,List<String> points,List<String> questions,Instant savedAt){}
 public record AiSummaryInput(String provider,String summary,List<String> points,List<String> questions){}
 public record LocalInput(Long testerId,boolean testerOverride,Long moduleId,Long technologyId,String callNumber,Long testPlanId){}
 public View view(Demand d){return new View(d.jiraKey,d.summary,d.description,d.statusId,d.statusName,d.assigneeName,d.assigneeAccountId,d.projectKey,d.squad,d.sprint,d.jiraUpdated,d.syncedAt,d.tester==null?null:d.tester.getId(),d.tester==null?null:d.tester.getName(),d.testerOverride,d.module==null?null:d.module.getId(),d.module==null?null:d.module.getName(),d.technology==null?null:d.technology.getId(),d.technology==null?null:d.technology.getName(),d.callNumber,d.testPlan==null?null:d.testPlan.getId(),storedSummary(d));}
 private AiSummary storedSummary(Demand d){
  if(d.aiSummary==null||d.aiSummary.isBlank())return null;
  try{var json=mapper.readTree(d.aiSummary);
   return new AiSummary(d.aiSummaryProvider,json.path("summary").asText(""),strings(json.path("points")),strings(json.path("questions")),d.aiSummaryAt);}
  catch(Exception e){return new AiSummary(d.aiSummaryProvider,d.aiSummary,List.of(),List.of(),d.aiSummaryAt);}
 }
 private List<String> strings(JsonNode node){var values=new ArrayList<String>();if(node.isArray())for(var item:node)if(item.isTextual())values.add(item.asText());return values;}
 @Transactional public View saveAiSummary(String key,AiSummaryInput input){
  var d=require(key);
  if(input==null||input.summary()==null||input.summary().isBlank())throw new IllegalArgumentException("Gere o resumo antes de salvar.");
  if(input.summary().length()>4000)throw new IllegalArgumentException("O resumo deve ter até 4 mil caracteres.");
  var node=mapper.createObjectNode();node.put("summary",input.summary().trim());
  capped(node.putArray("points"),input.points(),6);capped(node.putArray("questions"),input.questions(),4);
  d.aiSummary=node.toString();d.aiSummaryProvider=input.provider()==null||input.provider().isBlank()?null:input.provider().trim();d.aiSummaryAt=Instant.now();
  return view(repository.save(d));
 }
 private void capped(com.fasterxml.jackson.databind.node.ArrayNode target,List<String> values,int max){
  if(values==null)return;int added=0;
  for(String value:values){if(value==null||value.isBlank())continue;if(added==max)break;
   if(value.length()>500)throw new IllegalArgumentException("Cada item deve ter até 500 caracteres.");target.add(value.trim());added++;}
 }
 public Demand require(String key){return repository.findById(key).orElseThrow(()->new jakarta.persistence.EntityNotFoundException());}
 @Transactional(readOnly=true) public List<View> list(){return repository.findAll().stream().map(this::view).sorted(Comparator.comparing(View::jiraKey)).toList();}
 @Transactional(readOnly=true) public View get(String key){return view(require(key));}
 @Transactional public View sync(String key){return persist(jira.issue(key));}
 /** Remove apenas a demanda local. A issue no Jira e o plano de testes (com suas
  * suítes, casos e evidências) permanecem intactos. */
 @Transactional public void delete(String key){repository.delete(require(key));}
 private View persist(JsonNode issue){
  String key=issue.path("key").asText();var d=repository.findById(key).orElseGet(Demand::new);d.jiraKey=key;var f=issue.path("fields");var s=settings.setting("jira");
  d.summary=f.path("summary").asText();d.description=text(f.path("description"));d.statusId=f.path("status").path("id").asText();d.statusName=f.path("status").path("name").asText();d.projectKey=f.path("project").path("key").asText();
  d.assigneeAccountId=f.path("assignee").path("accountId").asText(null);d.assigneeName=f.path("assignee").path("displayName").asText(null);
  d.squad=s.squadField==null?null:label(f.path(s.squadField));d.sprint=s.sprintField==null?null:label(f.path(s.sprintField));
  if(!d.testerOverride && s.testerField!=null){var t=f.path(s.testerField);if(t.isArray())t=t.path(0);d.tester=t.hasNonNull("accountId")?users.tester(t.path("accountId").asText(),t.path("displayName").asText()):null;}
  d.jiraUpdated=f.path("updated").asText();d.syncedAt=Instant.now();
  if(d.testPlan!=null){d.testPlan.setDeveloper(d.assigneeAccountId==null?null:users.developer(d.assigneeAccountId,d.assigneeName));d.testPlan.setTester(d.tester);plans.save(d.testPlan);}
  return view(repository.save(d));
 }
 @Transactional public View transition(String key,String id){require(key);return persist(jira.transition(key,id));}
 @Transactional public View transition(String key,String id,JsonNode comment){require(key);return persist(jira.transition(key,id,comment));}
 @Transactional public View update(String key,LocalInput input){var d=require(key);d.testerOverride=input.testerOverride();d.tester=input.testerId()==null?null:testers.findById(input.testerId()).orElseThrow(()->new IllegalArgumentException("Tester inválido."));d.module=input.moduleId()==null?null:modules.findById(input.moduleId()).orElseThrow();d.technology=input.technologyId()==null?null:technologies.findById(input.technologyId()).orElseThrow();d.callNumber=input.callNumber();
  if(input.testPlanId()!=null){var p=plans.findById(input.testPlanId()).orElseThrow();if(!d.jiraKey.equalsIgnoreCase(p.getJira()))throw new IllegalArgumentException("O plano deve pertencer à mesma Jira Key.");if(d.testPlan==null) inheritLocalMetadata(d,p);d.testPlan=p;}
  if(d.testPlan!=null){d.testPlan.setTester(d.tester);d.testPlan.setSystemModule(d.module);d.testPlan.setCallNumber(d.callNumber);d.testPlan.setDeveloper(d.assigneeAccountId==null?null:users.developer(d.assigneeAccountId,d.assigneeName));plans.save(d.testPlan);}repository.save(d);return d.testerOverride?view(d):sync(key);
 }
 @Transactional public View ensurePlan(String key){var d=require(key);if(d.testPlan==null){var existing=plans.findByJiraIgnoreCase(key);if(existing.size()>1)throw new IllegalStateException("Há vários planos com esta key. Selecione o plano existente.");var p=existing.isEmpty()?new TestPlan():existing.getFirst();if(p.getId()==null){p.setName(d.summary);p.setJira(key);p.setObservation(d.description);p.setData(LocalDate.now());p.setStatus(Status.EM_PROGRESSO);p.setCreated(false);}if(p.getId()!=null) inheritLocalMetadata(d,p);p.setDeveloper(d.assigneeAccountId==null?null:users.developer(d.assigneeAccountId,d.assigneeName));p.setTester(d.tester);p.setSystemModule(d.module);p.setCallNumber(d.callNumber);d.testPlan=plans.save(p);repository.save(d);}return view(d);}
 private void inheritLocalMetadata(Demand demand,TestPlan plan){if(demand.tester==null && !demand.testerOverride && plan.getTester()!=null){demand.tester=plan.getTester();demand.testerOverride=true;}if(demand.module==null)demand.module=plan.getSystemModule();if(demand.callNumber==null||demand.callNumber.isBlank())demand.callNumber=plan.getCallNumber();}
 public static String text(JsonNode n){if(n==null||n.isNull()||n.isMissingNode())return "";if(n.isTextual())return n.asText();StringBuilder out=new StringBuilder();if(n.has("text"))out.append(n.path("text").asText());for(var child:n.path("content"))out.append(text(child));if(Set.of("paragraph","heading","listItem","hardBreak").contains(n.path("type").asText()))out.append("\n");return out.toString();}
 private String label(JsonNode n){if(n.isNull()||n.isMissingNode())return null;if(n.isArray()){var labels=new ArrayList<String>();for(var item:n){String value=label(item);if(value!=null)labels.add(value);}return String.join(", ",labels);}if(n.isValueNode())return n.asText();for(String key:List.of("name","value","displayName"))if(n.hasNonNull(key))return n.path(key).asText();return null;}
}
