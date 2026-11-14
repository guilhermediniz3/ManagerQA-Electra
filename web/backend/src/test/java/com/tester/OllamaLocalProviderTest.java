package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.ai.AiTransport;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import org.junit.jupiter.api.*;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * O provider de IA que roda na própria máquina.
 *
 * Ele não tem credencial — quem serve o modelo é o computador de quem usa — e
 * era por aí que o caminho quebrava: o app tratava "sem chave" como "não
 * configurado", e a validação do nome do modelo não aceitava o dois-pontos que
 * o Ollama usa. Este teste percorre o que a tela percorre: descobrir os modelos
 * e pedir um texto.
 */
class OllamaLocalProviderTest {

    private OllamaDeMentira servico;
    private AiTransport transporte;
    private IntegrationSetting setting;

    @BeforeEach
    void subir() throws Exception {
        servico = new OllamaDeMentira(caminho -> caminho.endsWith("/models")
            ? "{\"data\":[{\"id\":\"qwen2.5:3b\"}]}"
            : "{\"choices\":[{\"message\":{\"content\":"
              + "\"Um DRE é o demonstrativo de resultado do exercício.\"}}]}");

        setting = new IntegrationSetting();
        setting.provider = "ollama";
        setting.model = "qwen2.5:3b";
        setting.baseUrl = servico.endereco();

        var settings = mock(IntegrationService.class);
        when(settings.setting("ollama")).thenReturn(setting);
        when(settings.secret("ollama")).thenReturn("");
        var mapper = new ObjectMapper();
        transporte = new AiTransport(settings, mapper, new JsonHttpTransport(mapper));
    }

    @AfterEach
    void derrubar() throws Exception { servico.close(); }

    @Test
    void listaModelosSemCredencial() {
        assertThat(transporte.models("ollama")).containsExactly("qwen2.5:3b");
        assertThat(servico.caminhos).containsExactly("/v1/models");
        // Sem chave não se manda cabeçalho de autorização: um "Bearer " vazio é
        // o tipo de coisa que um serviço local recusa.
        assertThat(servico.autorizacoes).containsExactly((String) null);
    }

    /** O nome do modelo do Ollama traz o tamanho depois de dois-pontos. */
    @Test
    void respondeTextoLivreComModeloQueTemDoisPontos() {
        assertThat(transporte.texto("ollama", "O que é um DRE?", 800))
            .contains("demonstrativo de resultado");
        assertThat(servico.caminhos).containsExactly("/v1/chat/completions");
    }

    @Test
    void enderecoComBarraNoFimNaoDuplicaCaminho() {
        setting.baseUrl = servico.endereco() + "/";
        transporte.test("ollama");
        assertThat(servico.caminhos).containsExactly("/v1/models");
    }

    /**
     * Serviço fora do ar é o caso mais comum de "não funciona": a máquina não
     * está servindo modelo nenhum. A mensagem precisa dizer onde se procurou e
     * o que fazer, em vez do "indisponível ou resposta inválida" genérico.
     */
    @Test
    void servicoForaDoArDizOndeProcurouEOQueFazer() throws Exception {
        int fechada;
        try (var porta = new ServerSocket(0)) { fechada = porta.getLocalPort(); }
        setting.baseUrl = "http://127.0.0.1:" + fechada;
        assertThatThrownBy(() -> transporte.test("ollama"))
            .hasMessageContaining(String.valueOf(fechada))
            .hasMessageContaining("em execução");
    }

    /**
     * O app desktop escolhe a porta ao subir o serviço embutido e avisa o
     * backend por variável de ambiente. Sem endereço na tela nem variável, vale
     * o localhost padrão.
     */
    @Test
    void semEnderecoNaTelaOPadraoEOLocalhost() {
        setting.baseUrl = null;
        assertThat(transporte.enderecoDaIaLocal()).isEqualTo("http://127.0.0.1:11434");
    }
}
