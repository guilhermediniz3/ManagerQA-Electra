package com.tester;

import com.tester.integration.config.IntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:integration_save;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class IntegrationSaveTest {
    @Autowired IntegrationService service;

    private IntegrationService.Input aiInput(String model, String secret) {
        return new IntegrationService.Input(null, null, model, secret, null, null, null, false);
    }

    @Test
    void savesAiCredentialWithoutModel() {
        var saved = service.save("groq", aiInput(null, "gsk_dummy_key_for_test_only"));
        assertThat(saved.provider()).isEqualTo("groq");
        assertThat(saved.maskedSecret()).endsWith("only");
        assertThat(saved.status()).isEqualTo("UNTESTED");
        assertThat(service.secret("groq")).isEqualTo("gsk_dummy_key_for_test_only");
    }

    @Test
    void aiSaveIgnoresJiraFieldValidation() {
        var input = new IntegrationService.Input(
            "https://not-a-jira-url", "whatever", "llama-3.3-70b-versatile",
            "gsk_dummy_key_for_test_only", "squad", "sprint", "tester", true);
        var saved = service.save("groq", input);
        assertThat(saved.model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(saved.defaultProvider()).isTrue();
        assertThat(saved.squadField()).isNull();
    }

    @Test
    void keepsCredentialWhenSecretOmitted() {
        service.save("openai", aiInput("gpt-4o-mini", "sk_dummy_key_for_test_only"));
        var again = service.save("openai", aiInput("gpt-4o", null));
        assertThat(again.maskedSecret()).endsWith("only");
        assertThat(again.model()).isEqualTo("gpt-4o");
    }

    @Test
    void rejectsShortCredential() {
        assertThatThrownBy(() -> service.save("groq", aiInput("m", "short")))
            .hasMessageContaining("pelo menos 8 caracteres");
    }

    @Test
    void stillValidatesJiraFields() {
        var input = new IntegrationService.Input(
            "https://example.atlassian.net", "qa@example.test", null, null,
            "squad", null, null, false);
        assertThatThrownBy(() -> service.save("jira", input))
            .hasMessageContaining("campo customizado");
    }

    /**
     * A IA local nao tem credencial: quem serve o modelo e a propria maquina.
     * Marca-la como "nao configurada" por falta de chave a fazia sumir das
     * telas de escolha mesmo instalada e pronta.
     */
    @Test
    void localProviderIsUsableWithoutCredential() {
        var saved = service.save("ollama", aiInput("qwen2.5:3b", null));
        assertThat(saved.maskedSecret()).isEmpty();
        assertThat(saved.status()).isEqualTo("UNTESTED");
        assertThat(service.secret("ollama")).isEmpty();
    }

    @Test
    void localProviderKeepsItsAddress() {
        var input = new IntegrationService.Input(
            "http://192.168.0.9:11434/", null, "qwen2.5:3b", null, null, null, null, true);
        var saved = service.save("ollama", input);
        assertThat(saved.baseUrl()).isEqualTo("http://192.168.0.9:11434");
        assertThat(saved.defaultProvider()).isTrue();

        var semEndereco = service.save("ollama", aiInput("qwen2.5:3b", null));
        assertThat(semEndereco.baseUrl()).isNull();
    }

    @Test
    void localProviderRejectsAddressThatIsNotHttp() {
        var input = new IntegrationService.Input(
            "ollama.exe", null, "qwen2.5:3b", null, null, null, null, false);
        assertThatThrownBy(() -> service.save("ollama", input))
            .hasMessageContaining("endereço");
    }
}
