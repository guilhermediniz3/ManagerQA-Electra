package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.ai.AiTransport;
import com.tester.integration.ai.LocalModelService;
import com.tester.integration.config.IntegrationService;
import com.tester.integration.config.IntegrationSetting;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Gestão dos modelos da IA local.
 *
 * Trocar de modelo precisa ser coisa da tela: se dependesse de gerar instalador
 * novo, o modelo envelheceria junto com a versão instalada.
 */
class LocalModelServiceTest {

    private static final String DOIS_MODELOS = """
        {"models":[{"name":"qwen2.5:3b","size":1929912432},
                   {"name":"qwen2.5:7b","size":4700000000}]}""";

    private OllamaDeMentira servico;
    private LocalModelService modelos;
    private IntegrationSetting setting;

    @BeforeEach
    void subir() throws Exception {
        servico = new OllamaDeMentira(caminho -> DOIS_MODELOS);

        setting = new IntegrationSetting();
        setting.provider = "ollama";
        setting.model = "qwen2.5:3b";
        setting.baseUrl = servico.endereco();

        var settings = mock(IntegrationService.class);
        when(settings.setting("ollama")).thenReturn(setting);
        when(settings.secret("ollama")).thenReturn("");
        var mapper = new ObjectMapper();
        var transport = new JsonHttpTransport(mapper);
        modelos = new LocalModelService(
            settings, transport, new AiTransport(settings, mapper, transport));
    }

    @AfterEach
    void derrubar() throws Exception { servico.close(); }

    @Test
    void marcaQualModeloEstaEmUso() {
        var instalados = modelos.instalados();
        assertThat(instalados).extracting(LocalModelService.Modelo::name)
            .containsExactly("qwen2.5:3b", "qwen2.5:7b");
        assertThat(instalados.get(0).emUso()).isTrue();
        assertThat(instalados.get(1).emUso()).isFalse();
        assertThat(servico.caminhos).containsExactly("/api/tags");
    }

    /**
     * O endereço sai do mesmo lugar que o resto usa. Quando eram dois cálculos,
     * bastava um deles ignorar o que estava configurado para a tela listar os
     * modelos de um serviço e a geração falar com outro.
     */
    @Test
    void usaOEnderecoConfiguradoEmIntegracoes() {
        setting.baseUrl = servico.endereco() + "/v1/";
        assertThat(modelos.instalados()).isNotEmpty();
        assertThat(servico.caminhos).containsExactly("/api/tags");
    }

    @Test
    void baixarPedeOModeloEDevolveOQueChegou() {
        var baixado = modelos.baixar("qwen2.5:7b");
        assertThat(baixado.name()).isEqualTo("qwen2.5:7b");
        assertThat(servico.caminhos).containsExactly("/api/pull", "/api/tags");
        assertThat(servico.corpos.get(0)).contains("qwen2.5:7b");
    }

    /** Nome de modelo é concatenado numa URL: não pode aceitar qualquer coisa. */
    @Test
    void recusaNomeDeModeloForaDoFormato() {
        assertThatThrownBy(() -> modelos.baixar("../../etc/passwd"))
            .hasMessageContaining("inválido");
        assertThatThrownBy(() -> modelos.baixar("qwen2.5:3b/../outro"))
            .hasMessageContaining("inválido");
        assertThatThrownBy(() -> modelos.remover("qwen2.5:3b; rm -rf"))
            .hasMessageContaining("inválido");
        assertThat(servico.caminhos).isEmpty();
    }

    /** Nome com organização e etiqueta continua valendo. */
    @Test
    void aceitaNomeComNamespace() {
        assertThatCode(() -> modelos.remover("library/llama3.2:3b"))
            .doesNotThrowAnyException();
        assertThat(servico.caminhos).containsExactly("/api/delete");
    }

    /**
     * Remover o modelo em uso deixaria o sistema configurado para um modelo que
     * não existe mais — e a falha só apareceria na próxima geração.
     */
    @Test
    void naoRemoveOModeloQueEstaEmUso() {
        assertThatThrownBy(() -> modelos.remover("qwen2.5:3b"))
            .hasMessageContaining("modelo em uso");
        assertThat(servico.caminhos).isEmpty();

        modelos.remover("qwen2.5:7b");
        assertThat(servico.caminhos).containsExactly("/api/delete");
    }

    @Test
    void servicoForaDoArNaoDerrubaATela() throws Exception {
        servico.close();
        assertThat(modelos.disponivel()).isFalse();
    }

    /**
     * Numa máquina recém-instalada o modelo existe mas ninguém o escolheu, e
     * sem escolha a geração recusa. Como o aplicativo traz exatamente um,
     * perguntar qual seria uma pergunta de uma alternativa só.
     */
    @Test
    void adotaSozinhoOModeloQueVeioNoInstalador() throws Exception {
        setting.model = null;
        try (var soUm = new OllamaDeMentira(
                caminho -> "{\"models\":[{\"name\":\"qwen2.5:1.5b\",\"size\":941000000}]}")) {
            setting.baseUrl = soUm.endereco();
            var settings = mock(IntegrationService.class);
            when(settings.setting("ollama")).thenReturn(setting);
            when(settings.secret("ollama")).thenReturn("");
            var mapper = new ObjectMapper();
            var transport = new JsonHttpTransport(mapper);
            var local = new LocalModelService(settings, transport,
                    new AiTransport(settings, mapper, transport));

            assertThat(local.adotarModeloSeNecessario()).isEqualTo("qwen2.5:1.5b");
            verify(settings).save(eq("ollama"), any());
        }
    }

    /** Com mais de um baixado, a escolha continua sendo de quem usa. */
    @Test
    void comDoisModelosNaoEscolhePorNinguem() {
        setting.model = null;
        assertThat(modelos.adotarModeloSeNecessario()).isNull();
    }

    /**
     * O modelo escolhido pode ter sumido — é o que acontece com quem atualiza o
     * aplicativo e recebe outro modelo embutido. Manter a escolha antiga
     * deixaria a geração falhando contra um modelo que não existe.
     */
    @Test
    void trocaOModeloQueNaoEstaMaisInstalado() throws Exception {
        setting.model = "qwen2.5:3b";
        try (var soUm = new OllamaDeMentira(
                caminho -> "{\"models\":[{\"name\":\"qwen2.5:1.5b\",\"size\":941000000}]}")) {
            setting.baseUrl = soUm.endereco();
            var settings = mock(IntegrationService.class);
            when(settings.setting("ollama")).thenReturn(setting);
            when(settings.secret("ollama")).thenReturn("");
            var mapper = new ObjectMapper();
            var transport = new JsonHttpTransport(mapper);
            var local = new LocalModelService(settings, transport,
                    new AiTransport(settings, mapper, transport));

            assertThat(local.adotarModeloSeNecessario()).isEqualTo("qwen2.5:1.5b");
        }
    }

    /** Sumiu, mas há vários para escolher: aí a decisão volta para quem usa. */
    @Test
    void comVariosNaoEscolheNoLugarDeQuemUsa() {
        setting.model = "modelo-que-nao-existe";
        assertThat(modelos.adotarModeloSeNecessario()).isEqualTo("modelo-que-nao-existe");
    }

    @Test
    void naoTrocaOModeloJaEscolhido() {
        assertThat(modelos.adotarModeloSeNecessario()).isEqualTo("qwen2.5:3b");
    }
}
