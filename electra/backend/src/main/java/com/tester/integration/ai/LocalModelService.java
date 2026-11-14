package com.tester.integration.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tester.integration.IntegrationException;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.config.IntegrationService;

/**
 * Modelos da IA que roda na própria máquina.
 *
 * O aplicativo já vem com um modelo instalado, mas modelo envelhece: sai versão
 * melhor, ou a máquina de alguém comporta um maior. Estes métodos existem para
 * que trocar de modelo seja coisa da tela, e não motivo para gerar instalador
 * novo — baixar um modelo é conversa direta com o serviço local.
 */
@Service
public class LocalModelService {

    private final IntegrationService settings;
    private final JsonHttpTransport transport;
    private final AiTransport enderecos;

    public LocalModelService(IntegrationService settings, JsonHttpTransport transport,
                             AiTransport enderecos) {
        this.settings = settings;
        this.transport = transport;
        this.enderecos = enderecos;
    }

    /** Modelo, tamanho em bytes e se é o que está configurado para uso. */
    public record Modelo(String name, long size, boolean emUso) {}

    /**
     * Sugestões para quem não sabe o que escolher. O tamanho manda mais do que a
     * marca: um modelo maior do que a memória livre não responde, ele trava a
     * máquina.
     */
    public record Sugestao(String name, String label, String description, double gigabytes) {}

    private static final List<Sugestao> SUGERIDOS = List.of(
            new Sugestao("qwen2.5:3b", "Leve",
                    "Roda em qualquer máquina. Bom para o chat da documentação.", 1.9),
            new Sugestao("llama3.2:3b", "Leve (alternativo)",
                    "Parecido com o anterior, de outra família.", 2.0),
            new Sugestao("qwen2.5:7b", "Médio",
                    "Escreve documentação bem melhor. Precisa de ~8 GB de memória livre.", 4.7),
            new Sugestao("qwen2.5:14b", "Grande",
                    "Qualidade próxima da nuvem. Só em máquina com 16 GB ou mais.", 9.0));

    public List<Sugestao> sugeridos() {
        return SUGERIDOS;
    }

    /**
     * Formato de nome de modelo: segmentos separados por barra, cada um
     * começando por letra ou número, e a etiqueta de tamanho depois dos dois
     * pontos — "qwen2.5:3b", "library/llama3.2:3b".
     *
     * O padrão anterior aceitava ponto e barra soltos em qualquer posição, o
     * que deixava passar "../.." — e esse nome chega a este serviço pela URL
     * do endpoint de remoção.
     */
    private static final java.util.regex.Pattern NOME_DE_MODELO =
            java.util.regex.Pattern.compile(
                    "[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*){0,2}(:[A-Za-z0-9][A-Za-z0-9._-]*)?");

    private static void exigirNomeValido(String nome) {
        if (nome == null || nome.length() > 100 || !NOME_DE_MODELO.matcher(nome).matches())
            throw new IllegalArgumentException("Nome de modelo inválido.");
    }

    /**
     * Adota o modelo que veio instalado, quando ainda não há nenhum escolhido.
     *
     * Numa máquina recém-instalada o modelo existe mas ninguém o escolheu, e
     * sem escolha a geração recusa com "configure um modelo". Como o aplicativo
     * traz exatamente um, perguntar qual seria uma pergunta de uma alternativa
     * só. Havendo mais de um, a escolha continua sendo de quem usa.
     *
     * @return o modelo em uso depois da adoção, ou nulo se não deu para decidir
     */
    @org.springframework.transaction.annotation.Transactional
    public String adotarModeloSeNecessario() {
        var config = settings.setting("ollama");

        List<Modelo> baixados;
        try {
            baixados = instalados();
        } catch (RuntimeException servicoForaDoAr) {
            return null;
        }

        boolean escolhido = config.model != null && !config.model.isBlank();
        // O modelo escolhido antes pode não estar mais aqui: é o que acontece
        // com quem atualiza o aplicativo e recebe outro modelo embutido, ou
        // remove um modelo pela tela. Manter a escolha antiga deixaria a
        // geração falhando contra um modelo que não existe.
        boolean existeAinda = escolhido && baixados.stream()
                .anyMatch(m -> m.name().equals(config.model));
        if (escolhido && existeAinda) return config.model;
        if (baixados.size() != 1) return escolhido ? config.model : null;

        String unico = baixados.get(0).name();
        settings.save("ollama", new IntegrationService.Input(config.baseUrl, null, unico,
                null, null, null, null, config.defaultProvider));
        return unico;
    }

    /** O que já está baixado nesta máquina. */
    public List<Modelo> instalados() {
        String emUso = settings.setting("ollama").model;
        JsonNode resposta = chamar("/api/tags", null);
        List<Modelo> modelos = new ArrayList<>();
        for (JsonNode item : resposta.path("models")) {
            String nome = item.path("name").asText("");
            if (nome.isBlank()) continue;
            modelos.add(new Modelo(nome, item.path("size").asLong(0), nome.equals(emUso)));
        }
        return modelos;
    }

    /**
     * Baixa um modelo. A chamada volta só quando termina — pode levar minutos,
     * e é por isso que a tela avisa antes de começar em vez de deixar a pessoa
     * achando que travou.
     */
    public Modelo baixar(String nome) {
        exigirNomeValido(nome);
        chamar("/api/pull", Map.of("model", nome, "stream", false));
        return instalados().stream()
                .filter(modelo -> modelo.name().equals(nome) || modelo.name().startsWith(nome + ":"))
                .findFirst()
                .orElseThrow(() -> new IntegrationException(
                        "O download terminou mas o modelo não apareceu na lista. Tente de novo."));
    }

    public void remover(String nome) {
        exigirNomeValido(nome);
        if (nome.equals(settings.setting("ollama").model))
            throw new IllegalArgumentException(
                    "Este é o modelo em uso. Escolha outro antes de remover este.");
        chamar("/api/delete", Map.of("model", nome));
    }

    /** O serviço local está de pé? A tela usa isto para não oferecer o que não funciona. */
    public boolean disponivel() {
        try {
            chamar("/api/tags", null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private JsonNode chamar(String caminho, Object corpo) {
        // O endereço sai do transporte: era calculado aqui também, e duas
        // cópias da mesma regra acabam discordando.
        String base = enderecos.enderecoDaIaLocal();
        // Baixar modelo leva minutos; o tempo limite curto do resto do sistema
        // faria a operação parecer falha justamente quando está funcionando.
        return transport.request(base + caminho, Map.of(), corpo, 30 * 60 * 1000, "IA local");
    }
}
