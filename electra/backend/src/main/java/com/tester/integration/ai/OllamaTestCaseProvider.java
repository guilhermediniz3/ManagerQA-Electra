package com.tester.integration.ai;

import org.springframework.stereotype.Component;

/**
 * IA rodando na própria máquina, pelo Ollama.
 *
 * Existe para que o sistema não fique preso a um token de terceiro: o conteúdo
 * dos casos de teste e da documentação não sai do computador, e não há chave,
 * cota nem cobrança por uso. Fala o mesmo protocolo dos provedores compatíveis
 * com OpenAI, então reaproveita todo o caminho já existente.
 */
@Component
public class OllamaTestCaseProvider implements TestCaseAiProvider {

    private final AiTransport transport;

    public OllamaTestCaseProvider(AiTransport transport) {
        this.transport = transport;
    }

    public String id() {
        return "ollama";
    }

    public String generate(String description) {
        return transport.generate(id(), description);
    }

    public String summarize(String description) {
        return transport.summarize(id(), description);
    }

    public String texto(String prompt, int maxTokens) {
        return transport.texto(id(), prompt, maxTokens);
    }

    public java.util.List<String> models() {
        return transport.models(id());
    }

    public void testConnection() {
        transport.test(id());
    }
}
