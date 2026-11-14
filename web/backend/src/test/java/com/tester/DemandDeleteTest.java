package com.tester;

import java.time.LocalDate;
import com.tester.demand.Demand;
import com.tester.demand.DemandRepository;
import com.tester.demand.DemandService;
import com.tester.entity.TestPlan;
import com.tester.enuns.Status;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.jira.service.JiraIssueService;
import com.tester.repository.TestPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:demand_delete;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class DemandDeleteTest {
    @Autowired DemandService service;
    @Autowired DemandRepository demands;
    @Autowired TestPlanRepository plans;
    @MockitoBean JiraIssueService jira;
    @MockitoBean IntegrationService settings;

    private Long planId;

    @BeforeEach
    void seed() {
        demands.deleteAll();
        var plan = new TestPlan();
        plan.setName("Plano SPROD-134");
        plan.setJira("SPROD-134");
        plan.setData(LocalDate.now());
        plan.setStatus(Status.EM_PROGRESSO);
        planId = plans.save(plan).getId();

        var demand = new Demand();
        demand.jiraKey = "SPROD-134";
        demand.summary = "DRE por Caixa";
        demand.testPlan = plan;
        demands.save(demand);
    }

    @Test
    void removesOnlyTheLocalDemand() {
        service.delete("SPROD-134");
        assertThat(demands.findById("SPROD-134")).isEmpty();
    }

    @Test
    void keepsTheLinkedTestPlan() {
        service.delete("SPROD-134");
        assertThat(plans.findById(planId)).isPresent();
    }

    @Test
    void deletingUnknownDemandIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.delete("NOPE-1"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void demandCanBeReimportedAfterDeletion() {
        service.delete("SPROD-134");
        var again = new Demand();
        again.jiraKey = "SPROD-134";
        again.summary = "DRE por Caixa";
        demands.save(again);
        assertThat(service.get("SPROD-134").summary()).isEqualTo("DRE por Caixa");
    }
}
