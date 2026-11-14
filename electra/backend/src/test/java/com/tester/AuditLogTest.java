package com.tester;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.audit.AuditLog;
import com.tester.audit.AuditLogRepository;
import com.tester.entity.Developer;
import com.tester.entity.User;
import com.tester.repository.DeveloperRepository;
import com.tester.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuditLogRepository logs;
    @Autowired UserRepository users;
    @Autowired DeveloperRepository developers;
    @Autowired PlatformTransactionManager transactionManager;
    private User actor;
    private String authorization;

    @Autowired org.springframework.security.crypto.password.PasswordEncoder senhas;
    @Autowired com.tester.infra.security.TokenService tokens;

    /**
     * A conta do teste nasce direto no banco.
     *
     * Aqui o que interessa é o registro da auditoria, não o caminho do
     * cadastro.
     */
    @BeforeEach void registerActor() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@example.test";
        User conta = new User("Auditoria QA", email,
                senhas.encode("audit-password-must-not-appear"), true);
        actor = users.save(conta);
        authorization = "Bearer " + tokens.generationToken(actor);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void recordsCrudWithGeneratedIdAndActorWithoutPayload() throws Exception {
        long id = createDeveloper("private-record-description", List.of());
        mvc.perform(put("/developers/" + id).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"changed-private-description\",\"active\":true}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/developers/" + id).header("Authorization", authorization)).andExpect(status().isNoContent());
        var records = entityLogs("Developer", id);
        assertThat(records).extracting(AuditLog::getAction).containsExactly("CREATE", "UPDATE", "DELETE");
        assertThat(records).allSatisfy(log -> {
            assertThat(log.getActorId()).isEqualTo(actor.getId());
            assertThat(log.getActorName()).isEqualTo("Auditoria QA");
            assertThat(log.getActorEmail()).isEqualTo(actor.getEmail());
            assertThat(log.getOccurredAt()).isNotNull();
        });
        String json = mvc.perform(get("/api/audit-logs").header("Authorization", authorization)
                .param("entityType", "Developer").param("action", "DELETE").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].entityId").value(String.valueOf(id)))
            .andExpect(jsonPath("$.content[0].action").value("DELETE")).andExpect(jsonPath("$.size").value(1))
            .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("private-record-description", "changed-private-description",
            "audit-password-must-not-appear", authorization, "password", "token");
    }

    @Test void rollbackRemovesAuditAlongsideInsertedRecord() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        long before = logs.count();
        AtomicReference<Long> id = new AtomicReference<>();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var saved = developers.saveAndFlush(new Developer("rolled-back", true));
            id.set(saved.getId());
            assertThat(logs.count()).isEqualTo(before + 1);
            throw new IllegalStateException("force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(id.get()).isNotNull();
        assertThat(developers.findById(id.get())).isEmpty();
        assertThat(logs.count()).isEqualTo(before);
    }

    @Test void associationOnlyChangeIsAuditedOnce() throws Exception {
        var technology = mvc.perform(post("/technologies").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Audit technology\",\"active\":true}"))
            .andExpect(status().isCreated()).andReturn();
        long technologyId = mapper.readTree(technology.getResponse().getContentAsString()).path("id").asLong();
        long id = createDeveloper("Audit association", List.of(technologyId));
        assertThat(entityLogs("Developer", id)).extracting(AuditLog::getAction).containsExactly("CREATE");
        mvc.perform(delete("/developers/" + id + "/technology/" + technologyId)
                .header("Authorization", authorization)).andExpect(status().isOk());
        assertThat(entityLogs("Developer", id)).extracting(AuditLog::getAction).containsExactly("CREATE", "UPDATE");
    }

    @Test void readsAndRejectedWritesDoNotCreateLogsAndListingRequiresAuthentication() throws Exception {
        long before = logs.count();
        mvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/developers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"unauthorized\",\"active\":true}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/developers/9223372036854775807").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"missing\",\"active\":true}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/audit-logs").header("Authorization", authorization)).andExpect(status().isOk());
        mvc.perform(get("/api/audit-logs").header("Authorization", authorization).param("size", "101"))
            .andExpect(status().isBadRequest());
        assertThat(logs.count()).isEqualTo(before);
    }

    @Test void configurationIsAuditedWithoutSecretOrIntegrationUsername() throws Exception {
        mvc.perform(put("/api/integrations/openai").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                    "secret", "super-private-api-token-987654", "username", "private-integration-user",
                    "model", "example-model", "defaultProvider", false))))
            .andExpect(status().isOk());
        String json = mvc.perform(get("/api/audit-logs").header("Authorization", authorization)
                .param("entityType", "IntegrationSetting"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].entityId").value("openai"))
            .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("super-private-api-token-987654", "private-integration-user", "example-model",
            "encryptedSecret", "secretSuffix", "maskedSecret");
    }

    private long createDeveloper(String name, List<Long> technologies) throws Exception {
        var result = mvc.perform(post("/developers").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                    Map.of("name", name, "active", true, "technologyIds", technologies))))
            .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }
    private List<AuditLog> entityLogs(String type, long id) {
        return logs.findAll().stream().filter(log -> log.getEntityType().equals(type)
            && log.getEntityId().equals(String.valueOf(id))).sorted(java.util.Comparator.comparing(AuditLog::getId)).toList();
    }
}
