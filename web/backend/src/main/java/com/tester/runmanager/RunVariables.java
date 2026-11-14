package com.tester.runmanager;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.tester.runmanager.entity.RunEnvironment;
import com.tester.runmanager.entity.RunSystem;

/**
 * O ambiente do processo que vai rodar o teste.
 *
 * É aqui que "HML" e "PROD" deixam de ser rótulo e passam a mudar alguma coisa.
 * O projeto de automação escolhe a URL pelo que encontra no ambiente do
 * processo — no caso deste time, a variável {@code ENV} —, então um card
 * marcado como produção que não passe nada rodaria em homologação com o botão
 * vermelho, que é a pior combinação possível: o aviso diz uma coisa e a
 * execução faz outra.
 *
 * A precedência vai do mais genérico para o mais específico, e o último vence:
 * o que já existe na máquina, depois o que o sistema define, e por fim o
 * ambiente. É a mesma ordem que o projeto de testes usa para as próprias
 * configurações.
 */
public final class RunVariables {

    private RunVariables() {
    }

    /**
     * Nome da variável que o projeto de testes lê para saber onde rodar.
     *
     * Fica como padrão porque é a convenção deste time; um ambiente que use
     * outro nome preenche o campo de variáveis e manda nele.
     */
    static final String VARIAVEL_PADRAO = "ENV";

    public static Map<String, String> montar(RunSystem sistema, RunEnvironment ambiente) {
        Map<String, String> variaveis = new LinkedHashMap<>();
        if (sistema != null) acrescentar(variaveis, sistema.getVariables());
        if (ambiente != null) {
            String declaradas = ambiente.getVariables();
            if (declaradas == null || declaradas.isBlank()) {
                // Sem nada declarado, vale a convenção: o identificador do
                // ambiente é justamente "hml", "prod" — o que o projeto espera.
                variaveis.put(VARIAVEL_PADRAO, ambiente.getKey());
            } else {
                acrescentar(variaveis, declaradas);
            }
        }
        return variaveis;
    }

    /**
     * Lê o texto de variáveis: uma por linha, CHAVE=valor.
     *
     * Linha em branco e linha começando por # são ignoradas — quem escreve
     * costuma comentar para lembrar por que aquela variável está ali, e tratar
     * o comentário como variável daria um nome de variável impossível.
     */
    static void acrescentar(Map<String, String> destino, String texto) {
        if (texto == null || texto.isBlank()) return;
        for (String linha : texto.split("\\R")) {
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) continue;
            int igual = limpa.indexOf('=');
            if (igual <= 0) continue;
            String chave = limpa.substring(0, igual).trim();
            String valor = limpa.substring(igual + 1).trim();
            if (chave.isEmpty()) continue;
            // Aspas em volta do valor são hábito de arquivo .env e não fazem
            // parte do valor: passá-las adiante faria a URL chegar com aspas.
            if (valor.length() >= 2
                    && ((valor.startsWith("\"") && valor.endsWith("\""))
                        || (valor.startsWith("'") && valor.endsWith("'"))))
                valor = valor.substring(1, valor.length() - 1);
            destino.put(chave.toUpperCase(Locale.ROOT), valor);
        }
    }
}
