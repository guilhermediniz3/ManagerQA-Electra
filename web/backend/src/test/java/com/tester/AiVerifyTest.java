package com.tester;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.ai.AiService;
import com.tester.integration.ai.TestCaseAiProvider;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** "Conectado" must mean usable: credential valid AND configured model available. */
class AiVerifyTest {
    private static final class FakeProvider implements TestCaseAiProvider {
        private final String id;
        private final List<String> models;
        FakeProvider(String id, List<String> models) { this.id = id; this.models = models; }
        @Override public String id() { return id; }
        @Override public String generate(String description) { return ""; }
        @Override public String summarize(String description) { return ""; }
        @Override public String texto(String prompt, int maxTokens) { return ""; }
        @Override public List<String> models() { return models; }
        @Override public void testConnection() {}
    }

    private AiService service(String provider, String savedModel, List<String> available) {
        var settings = mock(IntegrationService.class);
        var setting = new IntegrationSetting();
        setting.provider = provider;
        setting.model = savedModel;
        when(settings.setting(provider)).thenReturn(setting);
        return new AiService(List.of(new FakeProvider(provider, available)),
            new ObjectMapper(), settings, null, null);
    }

    @Test
    void rejectsModelMissingFromAccount() {
        var ai = service("groq", "groq", List.of("llama-3.3-70b-versatile", "gemma2-9b-it"));
        assertThatThrownBy(() -> ai.verify("groq"))
            .hasMessageContaining("não está disponível nesta conta");
    }

    @Test
    void rejectsBlankModel() {
        assertThatThrownBy(() -> service("groq", "  ", List.of("llama-3.3-70b-versatile")).verify("groq"))
            .hasMessageContaining("nenhum modelo está configurado");
    }

    @Test
    void acceptsModelPresentInAccount() {
        var ai = service("groq", "llama-3.3-70b-versatile", List.of("llama-3.3-70b-versatile"));
        assertThatCode(() -> ai.verify("groq")).doesNotThrowAnyException();
    }

    @Test
    void acceptsGeminiModelRegardlessOfPrefix() {
        var ai = service("gemini", "models/gemini-2.0-flash", List.of("gemini-2.0-flash"));
        assertThatCode(() -> ai.verify("gemini")).doesNotThrowAnyException();
    }

    @Test
    void acceptsAnyModelWhenProviderListsNone() {
        var ai = service("groq", "custom-model", List.of());
        assertThatCode(() -> ai.verify("groq")).doesNotThrowAnyException();
    }
}
