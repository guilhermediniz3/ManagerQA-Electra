package com.tester.runmanager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separa falha de produto de falha de automação.
 *
 * As regras são as mesmas do histórico gerencial do projeto de testes, e a
 * ordem em que são aplicadas é o que importa: uma falha marcada como regra de
 * negócio é regra de negócio mesmo que a mensagem também fale de locator, e uma
 * queda de rede não é bug do produto por mais que tenha derrubado um clique.
 *
 * Isso existe porque as duas coisas pedem ações opostas — uma vira demanda no
 * Jira, a outra vira conserto no teste — e misturá-las faz o painel apontar
 * defeito onde não há.
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    private static final Pattern REGRA_DE_NEGOCIO =
            Pattern.compile("\\[(?:REGRA[_ -]?NEGOCIO|BUSINESS[_ -]?RULE)\\]", Pattern.CASE_INSENSITIVE);

    private static final Pattern INFRAESTRUTURA = Pattern.compile(
            "net::ERR_|ECONNREFUSED|ECONNRESET|ENOTFOUND|ETIMEDOUT"
            + "|Target page, context or browser has been closed|browser has been closed"
            + "|storageState|credential|credencial|session expired|sess[aã]o expirada",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCATOR = Pattern.compile(
            "strict mode violation|waiting for (?:getBy|locator)"
            + "|locator\\.(?:click|fill|press|hover|check|uncheck|selectOption)|\\bselector\\b"
            + "|element(?:s)? (?:was|is|were) not (?:visible|attached|enabled|editable)"
            + "|not attached to the DOM|toBeVisible|toBeHidden|toBeEnabled|toBeEditable",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STATUS = Pattern.compile(
            "\\[BACKEND\\]|response\\.status\\s*\\(|\\bHTTP\\b|\\bstatus code\\b"
            + "|respond(?:s|ed)? with \\d{3}|API login failed:\\s*\\d{3}|returned \\d{3}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CODIGO = Pattern.compile(
            "(?:respond(?:s|ed)? with|returned|API login failed:|status(?:\\s*code)?\\D{0,20})\\s*(\\d{3})\\b",
            Pattern.CASE_INSENSITIVE);

    public record Classificacao(String bucket, String summary, Integer statusCode) {
    }

    public static Classificacao classificar(String detalhes) {
        String texto = detalhes == null ? "" : detalhes;
        String resumo = resumir(texto);
        Integer codigo = codigo(texto);

        if (REGRA_DE_NEGOCIO.matcher(texto).find())
            return new Classificacao("REGRA_NEGOCIO", resumo, codigo);
        if (INFRAESTRUTURA.matcher(texto).find())
            return new Classificacao("INFRA", resumo, codigo);
        if (STATUS.matcher(texto).find())
            return new Classificacao("STATUS_CODE", resumo, codigo);
        if (LOCATOR.matcher(texto).find())
            return new Classificacao("LOCATOR", resumo, codigo);
        return new Classificacao("OUTRO", resumo, codigo);
    }

    /**
     * O número só sai quando existe de verdade.
     *
     * "Timeout 45000ms exceeded" cai no mesmo balde de status, mas não tem
     * código nenhum — inventar um faria o painel exibir um 450 que ninguém
     * recebeu.
     */
    static Integer codigo(String detalhes) {
        Matcher m = CODIGO.matcher(detalhes);
        if (!m.find()) return null;
        int codigo = Integer.parseInt(m.group(1));
        return codigo >= 100 && codigo < 600 ? codigo : null;
    }

    /** A primeira linha com conteúdo: é o que cabe numa lista. */
    static String resumir(String detalhes) {
        String linha = detalhes.lines().map(String::trim).filter(l -> !l.isEmpty())
                .findFirst().orElse("Falha sem mensagem");
        String limpa = REGRA_DE_NEGOCIO.matcher(linha).replaceAll("").trim();
        return limpa.length() <= 600 ? limpa : limpa.substring(0, 600);
    }
}
