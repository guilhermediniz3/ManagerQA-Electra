package com.tester;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.web.servlet.MockMvc;import org.springframework.http.MediaType;import com.fasterxml.jackson.databind.ObjectMapper;import com.tester.repository.UserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.assertj.core.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") class ManagerFlowTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired UserRepository users;
 @Test void privateEndpointsRejectAnonymous()throws Exception{for(String url:new String[]{"/developers","/testplans","/api/integrations","/api/demands","/api/dashboard"})mvc.perform(get(url)).andExpect(status().isUnauthorized());}
 @Autowired org.springframework.security.crypto.password.PasswordEncoder senhas;
 @Autowired com.tester.infra.security.TokenService tokens;
 /**
  * A conta do fluxo nasce direto no banco, sem passar pela tela de cadastro: o
  * que este teste verifica é a hierarquia de plano, suíte e caso.
  */
 @Test void loginAndLocalTestHierarchy()throws Exception{
  String email="flow-"+System.nanoTime()+"@example.test";
  var conta=new com.tester.entity.User("QA Test",email,senhas.encode("Local-test-123!"),true);
  users.save(conta);
  String auth="Bearer "+tokens.generationToken(conta);
  assertThat(users.findByEmail(email).orElseThrow().getPassword()).startsWith("$2");
  mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("email",email,"password","Local-test-123!")))).andExpect(status().isOk());
  mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("email",email,"password","wrong")))).andExpect(status().isUnauthorized());
  var plan=mvc.perform(post("/testplans").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Plano local\",\"jira\":\"QA-123\",\"status\":\"EM_PROGRESSO\",\"data\":\"09/09/2026\"}")).andExpect(status().isOk()).andReturn();
  long planId=mapper.readTree(plan.getResponse().getContentAsString()).path("id").asLong();
  var suite=mvc.perform(post("/test-suites").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("testPlanId",planId,"codeSuite",1,"status","EM_PROGRESSO","data","09/09/2026")))).andExpect(status().isOk()).andReturn();
  long suiteId=mapper.readTree(suite.getResponse().getContentAsString()).path("id").asLong();
  mvc.perform(post("/testcases").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("testSuiteId",suiteId,"codeCase",1,"scenario","Validar login","expectedResult","Acesso permitido","status","CONCLUIDA","data","09/09/2026")))).andExpect(status().isOk());
  mvc.perform(get("/testcases/plan/"+planId+"/suite/"+suiteId).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$[0].scenario").value("Validar login"));
  mvc.perform(get("/relatorio-jira/excel").param("testSuiteId",String.valueOf(suiteId)).header("Authorization",auth)).andExpect(status().isOk());
  mvc.perform(get("/relatorio-jira/pdf").param("testSuiteId",String.valueOf(suiteId)).header("Authorization",auth)).andExpect(status().isOk());
  mvc.perform(post("/test-suites/clone/"+suiteId).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"codeSuite\":2,\"data\":\"09/09/2026\",\"status\":\"EM_PROGRESSO\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.testCaseId.length()").value(1));
 }
}
