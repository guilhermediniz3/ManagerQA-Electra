package com.tester;

import com.tester.runmanager.RunCatalogDTOs.*;
import com.tester.runmanager.RunCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Cadastro do RunManager.
 *
 * O que antes era constante no painel — quatro marcações, dois ambientes, três
 * versões — passa a ser cadastro, para que ligar um sistema novo não dependa de
 * publicar uma versão do aplicativo.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:run_catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RunCatalogTest {

    @Autowired RunCatalogService service;

    private SystemDTO sistema(String nome) {
        return new SystemDTO(null, nome, null, "ultracar/qa-automated-pr", "master", null, null, null, null, true, 0, List.of());
    }

    @Test
    void cadastraSistemaComVersoes() {
        var salvo = service.salvarSistema(sistema("Ultracar"));
        assertThat(salvo.id()).isNotNull();
        assertThat(salvo.key()).isEqualTo("ultracar");

        service.salvarVersao(new VersionDTO(null, salvo.id(), "V2", null, true, 0));
        service.salvarVersao(new VersionDTO(null, salvo.id(), "V3", null, true, 1));

        var comVersoes = service.listarSistemas().stream()
                .filter(s -> s.id().equals(salvo.id())).findFirst().orElseThrow();
        assertThat(comVersoes.versions()).extracting(VersionDTO::name).containsExactly("V2", "V3");
    }

    /**
     * A chave entra na linha de comando e em nome de pipeline. Aceitar acento ou
     * espaço aqui viraria erro lá adiante, quando já não se sabe de onde veio.
     */
    @Test
    void derivaChaveLimpaDoNome() {
        assertThat(service.salvarSistema(sistema("Porto Car")).key()).isEqualTo("porto-car");
        assertThat(service.salvarSistema(sistema("Gestão")).key()).isEqualTo("gestao");
    }

    @Test
    void recusaChaveRepetida() {
        service.salvarSistema(sistema("Repetido"));
        assertThatThrownBy(() -> service.salvarSistema(sistema("Repetido")))
                .hasMessageContaining("Já existe um sistema");
    }

    @Test
    void recusaNomeVazio() {
        assertThatThrownBy(() -> service.salvarSistema(sistema("  ")))
                .hasMessageContaining("Dê um nome");
    }

    /**
     * Produção é escolha de quem cadastra, e não dedução pelo nome: um ambiente
     * chamado "pré" pode estar apontando para dados reais, e é essa marca que
     * faz o botão pedir confirmação.
     */
    @Test
    void ambienteGuardaSeEProducao() {
        var hml = service.salvarAmbiente(new EnvironmentDTO(null, "Homologação", null, false, null, true, 0));
        var prod = service.salvarAmbiente(new EnvironmentDTO(null, "Produção", null, true, null, true, 1));
        assertThat(hml.key()).isEqualTo("homologacao");
        assertThat(hml.production()).isFalse();
        assertThat(prod.production()).isTrue();
    }

    @Test
    void marcacaoEntraSemPublicarVersao() {
        service.salvarTag(new TagDTO(null, "Smoke", null, "O essencial de cada módulo", true, 0));
        service.salvarTag(new TagDTO(null, "Journey", null, "Ponta a ponta", true, 1));
        assertThat(service.catalogo().tags()).extracting(TagDTO::key).contains("smoke", "journey");
    }

    /** Inativar mantém o histórico de execuções fazendo sentido; excluir, não. */
    @Test
    void inativarMantemOCadastroNaLista() {
        var ambiente = service.salvarAmbiente(new EnvironmentDTO(null, "Antigo", null, false, null, true, 0));
        var inativo = service.salvarAmbiente(new EnvironmentDTO(ambiente.id(), "Antigo", ambiente.key(),
                false, null, false, 0));
        assertThat(inativo.active()).isFalse();
        assertThat(service.catalogo().environments()).extracting(EnvironmentDTO::id).contains(ambiente.id());
    }

    @Test
    void excluirSistemaLevaAsVersoesJunto() {
        var s = service.salvarSistema(sistema("Descartavel"));
        service.salvarVersao(new VersionDTO(null, s.id(), "V1", null, true, 0));
        service.excluirSistema(s.id());
        assertThat(service.listarSistemas()).extracting(SystemDTO::id).doesNotContain(s.id());
    }
}
