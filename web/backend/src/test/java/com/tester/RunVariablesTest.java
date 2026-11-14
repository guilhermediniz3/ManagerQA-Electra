package com.tester;

import com.tester.runmanager.RunVariables;
import com.tester.runmanager.entity.RunEnvironment;
import com.tester.runmanager.entity.RunSystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * O que diferencia homologação de produção.
 *
 * O projeto de automação escolhe a URL pelo que encontra no ambiente do
 * processo. Sem passar nada, um card marcado como produção rodaria em
 * homologação com o botão vermelho — o aviso dizendo uma coisa e a execução
 * fazendo outra, que é a pior combinação possível.
 */
class RunVariablesTest {

    private static RunEnvironment ambiente(String chave, String variaveis, boolean producao) {
        var e = new RunEnvironment();
        e.setKey(chave);
        e.setProduction(producao);
        e.setVariables(variaveis);
        return e;
    }

    private static RunSystem sistema(String variaveis) {
        var s = new RunSystem();
        s.setKey("ultracar");
        s.setVariables(variaveis);
        return s;
    }

    /**
     * Sem nada declarado vale a convenção do projeto: ENV com o identificador
     * do ambiente, que é justamente "hml" e "prod".
     */
    @Test
    void semDeclararNadaUsaOIdentificadorDoAmbiente() {
        assertThat(RunVariables.montar(null, ambiente("hml", null, false)))
                .containsExactly(entry("ENV", "hml"));
        assertThat(RunVariables.montar(null, ambiente("prod", "  ", true)))
                .containsExactly(entry("ENV", "prod"));
    }

    @Test
    void ambienteDeclaraOQueQuiser() {
        var variaveis = RunVariables.montar(null,
                ambiente("prod", "ENV=prod\nBASE_URL=https://app.exemplo.com.br", true));
        assertThat(variaveis)
                .containsEntry("ENV", "prod")
                .containsEntry("BASE_URL", "https://app.exemplo.com.br");
    }

    /** O ambiente é o mais específico dos dois e vence o que o sistema definiu. */
    @Test
    void ambienteVenceOSistema() {
        var variaveis = RunVariables.montar(sistema("ENV=hml\nTIMEOUT=30"),
                ambiente("prod", "ENV=prod", true));
        assertThat(variaveis).containsEntry("ENV", "prod").containsEntry("TIMEOUT", "30");
    }

    /** Comentário e linha em branco são hábito de arquivo .env, não variável. */
    @Test
    void ignoraComentarioELinhaEmBranco() {
        var variaveis = RunVariables.montar(null, ambiente("hml",
                "# o que muda entre os ambientes\n\nENV=hml\n\n# fim", false));
        assertThat(variaveis).containsExactly(entry("ENV", "hml"));
    }

    /** Aspas são hábito de .env e não fazem parte do valor. */
    @Test
    void tiraAspasDoValor() {
        var variaveis = RunVariables.montar(null,
                ambiente("hml", "BASE_URL=\"https://hml.exemplo.com\"\nOUTRO='valor'", false));
        assertThat(variaveis)
                .containsEntry("BASE_URL", "https://hml.exemplo.com")
                .containsEntry("OUTRO", "valor");
    }

    @Test
    void linhaSemIgualNaoViraVariavel() {
        assertThat(RunVariables.montar(null, ambiente("hml", "isto nao e variavel\nENV=hml", false)))
                .containsExactly(entry("ENV", "hml"));
    }

    @Test
    void semAmbienteNaoInventaNada() {
        assertThat(RunVariables.montar(sistema("TIMEOUT=30"), null))
                .containsExactly(entry("TIMEOUT", "30"));
        assertThat(RunVariables.montar(null, null)).isEmpty();
    }
}
