package com.tester;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.entity.*;
import com.tester.repository.*;
import com.tester.infra.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:drive_web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class DriveWebTest {
    @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired TokenService tokens;
    @Autowired TestCaseRepository cases;@Autowired TestSuiteRepository suites;@Autowired TestPlanRepository plans;
    @Autowired ObjectMapper mapper;
    String auth(){
        var u=users.save(new User("Drive test","drive-"+System.nanoTime()+"@example.test","fixture-hash",true));
        return "Bearer "+tokens.generationToken(u);
    }
    @Test void onlyCallbackIsPublicAndInvalidStateIsRejected() throws Exception {
        mvc.perform(get("/api/drive/settings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drive/connect")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drive/cases/1/recording")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/testcases/1/evidence").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://vimeo.com/123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/drive/callback").param("state","unknown").param("code","secret-code"))
            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-code"))));
    }
    @Test void configurationBelongsToAuthenticatedUser() throws Exception {
        String a=auth(),b=auth();
        mvc.perform(put("/api/drive/settings").header("Authorization",a).contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientId\":\"fixture.apps.googleusercontent.com\",\"clientSecret\":\"fixture-secret\",\"folder\":\"folder_abc\",\"redirectUri\":\"http://localhost:8081/api/drive/callback\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasClientSecret").value(true))
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
        mvc.perform(get("/api/drive/settings").header("Authorization",b)).andExpect(status().isOk()).andExpect(jsonPath("$.folder").value(""));
    }
    @Test void evidencePatchKeepsScenarioAndOldEvidenceOnInvalidInput() throws Exception {
        var plan=new TestPlan();plan.setName("Plano Drive");plan.setJira("QA-DRIVE");plan=plans.save(plan);
        var suite=new TestSuite();suite.setTestPlan(plan);suite=suites.save(suite);
        var c=new TestCaseEntity();c.setScenario("Cenário original");c.setExpectedResult("Resultado esperado");
        c.setObtainedResult("Resultado obtido");c.setTestSuite(suite);c.setVideoEvidence("https://vimeo.com/123");c=cases.save(c);
        String auth=auth();
        mvc.perform(patch("/testcases/"+c.getId()+"/evidence").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://evil.test/video\"}")).andExpect(status().isBadRequest());
        assertThat(cases.findById(c.getId()).orElseThrow().getVideoEvidence()).isEqualTo("https://vimeo.com/123");
        mvc.perform(patch("/testcases/"+c.getId()+"/evidence").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://drive.google.com/file/d/video_123/view\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scenario").value("Cenário original"))
            .andExpect(jsonPath("$.expectedResult").value("Resultado esperado"))
            .andExpect(jsonPath("$.obtainedResult").value("Resultado obtido"));
        assertThat(cases.findById(c.getId()).orElseThrow().getVideoEvidence()).contains("drive.google.com");
    }
}
