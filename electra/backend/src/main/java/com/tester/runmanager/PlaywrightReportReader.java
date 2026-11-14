package com.tester.runmanager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lê o relatório que o Playwright deixa depois de rodar.
 *
 * A fonte é o {@code test-results/results.json} do reporter json — o mesmo
 * arquivo que o painel local usa. Ele traz, por caso que falhou, a mensagem de
 * erro e os anexos que o Playwright gravou: o vídeo e o screenshot, que são o
 * que transforma uma falha em demanda com evidência.
 *
 * Execução que não roda teste não gera relatório. Nesse caso a leitura devolve
 * vazio, sem erro: não haver falha para listar é um resultado, não um problema.
 */
@Component
public class PlaywrightReportReader {

    private final ObjectMapper mapper;

    public PlaywrightReportReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Falha(String title, String fullName, String file, String module, String version,
                        String details, String videoPath, String screenshotPath) {
    }

    /** @param raiz a pasta do projeto de automação */
    public List<Falha> ler(Path raiz) {
        Path relatorio = raiz.resolve("test-results").resolve("results.json");
        if (!Files.isRegularFile(relatorio)) return List.of();
        try {
            JsonNode json = mapper.readTree(Files.readString(relatorio));
            List<Falha> falhas = new ArrayList<>();
            percorrer(json.path("suites"), "", falhas);
            return falhas;
        } catch (Exception e) {
            // Relatório truncado ou de outra versão: melhor não mostrar falha
            // nenhuma do que mostrar falha inventada.
            return List.of();
        }
    }

    /**
     * O relatório aninha suítes dentro de suítes — arquivo, describe, describe
     * de dentro. O caminho completo é o que dá nome reconhecível ao caso.
     */
    private void percorrer(JsonNode suites, String caminho, List<Falha> falhas) {
        for (JsonNode suite : suites) {
            String titulo = suite.path("title").asText("");
            String completo = caminho.isEmpty() ? titulo : caminho + " › " + titulo;
            String arquivo = suite.path("file").asText(null);

            for (JsonNode spec : suite.path("specs")) {
                if (spec.path("ok").asBoolean(true)) continue;
                lerSpec(spec, completo, arquivo, falhas);
            }
            percorrer(suite.path("suites"), completo, falhas);
        }
    }

    private void lerSpec(JsonNode spec, String caminho, String arquivo, List<Falha> falhas) {
        String titulo = spec.path("title").asText("");
        String arquivoDoSpec = spec.path("file").asText(arquivo);

        for (JsonNode teste : spec.path("tests")) {
            for (JsonNode resultado : teste.path("results")) {
                String status = resultado.path("status").asText("");
                if (!"failed".equals(status) && !"timedOut".equals(status)) continue;

                falhas.add(new Falha(titulo,
                        caminho.isEmpty() ? titulo : caminho + " › " + titulo,
                        arquivoDoSpec,
                        modulo(arquivoDoSpec), versao(arquivoDoSpec),
                        erro(resultado),
                        anexo(resultado, "video"),
                        anexo(resultado, "screenshot")));
            }
        }
    }

    private static String erro(JsonNode resultado) {
        var erro = resultado.path("error");
        String mensagem = erro.path("message").asText("");
        String pilha = erro.path("stack").asText("");
        if (mensagem.isBlank() && pilha.isBlank()) {
            var erros = resultado.path("errors");
            if (erros.isArray() && !erros.isEmpty())
                mensagem = erros.get(0).path("message").asText("");
        }
        String texto = (mensagem + System.lineSeparator() + pilha).trim();
        return texto.isBlank() ? "Falha sem mensagem" : texto;
    }

    private static String anexo(JsonNode resultado, String nome) {
        for (JsonNode anexo : resultado.path("attachments")) {
            String tipo = anexo.path("name").asText("");
            String caminho = anexo.path("path").asText(null);
            if (caminho != null && !caminho.isBlank() && tipo.startsWith(nome)) return caminho;
        }
        return null;
    }

    /** `tests/v2/workorder/x.spec.ts` → módulo `workorder`, versão `v2`. */
    static String modulo(String arquivo) {
        String[] partes = partes(arquivo);
        return partes.length >= 3 ? partes[2] : null;
    }

    static String versao(String arquivo) {
        String[] partes = partes(arquivo);
        return partes.length >= 2 ? partes[1] : null;
    }

    private static String[] partes(String arquivo) {
        if (arquivo == null) return new String[0];
        String normalizado = arquivo.replace('\\', '/');
        int corte = normalizado.indexOf("tests/");
        if (corte >= 0) normalizado = normalizado.substring(corte);
        return normalizado.split("/");
    }
}
