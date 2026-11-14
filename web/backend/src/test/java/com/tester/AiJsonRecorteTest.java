package com.tester;

import com.tester.integration.ai.AiService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Leitura da resposta da IA quando ela não vem só o JSON.
 *
 * Modelo pequeno — o caso da IA local — costuma embrulhar a resposta: cerca de
 * markdown, uma frase de apresentação antes, um comentário depois. Sem recortar,
 * a leitura falhava e a tela mostrava o JSON cru no lugar do resumo.
 */
class AiJsonRecorteTest {

    @Test
    void jsonPuroPassaIntacto() {
        assertThat(AiService.recortarJson("{\"summary\":\"ok\"}"))
            .isEqualTo("{\"summary\":\"ok\"}");
    }

    /**
     * A cerca vem seguida de quebra de linha. O recorte usava o escape de espaço
     * do Java em vez do \\s de regex, então parava na quebra.
     */
    @Test
    void tiraCercaDeMarkdownComQuebraDeLinha() {
        String resposta = "```json\n{\"summary\":\"ok\"}\n```";
        assertThat(AiService.recortarJson(resposta)).isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void tiraConversaAntesEDepois() {
        String resposta = "Claro! Aqui está o resumo:\n"
            + "{\"summary\":\"ok\",\"points\":[]}\n"
            + "Espero ter ajudado.";
        assertThat(AiService.recortarJson(resposta))
            .isEqualTo("{\"summary\":\"ok\",\"points\":[]}");
    }

    /** Sem JSON nenhum, devolve o texto: vira o resumo em prosa, que é melhor que nada. */
    @Test
    void semJsonDevolveOTexto() {
        assertThat(AiService.recortarJson("  A demanda trata do DRE mensal.  "))
            .isEqualTo("A demanda trata do DRE mensal.");
    }

    @Test
    void nuloNaoQuebra() {
        assertThat(AiService.recortarJson(null)).isEmpty();
    }
}
