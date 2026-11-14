package com.tester.runmanager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tester.integration.IntegrationException;
import com.tester.integration.JsonHttpTransport;
import com.tester.integration.config.IntegrationService;

/**
 * Sobe o vídeo da falha para o Vimeo e devolve o link.
 *
 * O vídeo vai para o Vimeo em vez de ser anexado na demanda porque anexo de
 * vídeo consome o armazenamento do Jira rapidamente — é a mesma decisão que o
 * painel local já tinha tomado, e o protocolo aqui é o mesmo de lá: TUS em uma
 * tacada só, que serve porque vídeo de evidência é pequeno.
 *
 * As chamadas usam o mesmo transporte do resto das integrações, e o envio do
 * arquivo vai por uma conexão crua: é um PATCH de bytes, não de JSON.
 *
 * A credencial é um token pessoal do Vimeo, guardado nas integrações como
 * qualquer outra. Sem ele, a tela de falhas continua funcionando — só não
 * oferece o envio.
 */
@Component
public class VimeoUploader {

    private static final String VERSAO = "application/vnd.vimeo.*+json;version=3.4";

    private final IntegrationService integracoes;
    private final JsonHttpTransport transporte;

    public VimeoUploader(IntegrationService integracoes, JsonHttpTransport transporte) {
        this.integracoes = integracoes;
        this.transporte = transporte;
    }

    public boolean configurado() {
        return !token(false).isBlank();
    }

    /**
     * @param arquivo vídeo no disco
     * @param titulo  como o vídeo aparece no Vimeo
     * @return o endereço do vídeo
     */
    public String enviar(Path arquivo, String titulo) {
        String token = token(true);
        if (!Files.isRegularFile(arquivo))
            throw new IntegrationException("O vídeo não está mais no disco: " + arquivo);

        long tamanho;
        try {
            tamanho = Files.size(arquivo);
        } catch (Exception e) {
            throw new IntegrationException("Não consegui ler o vídeo: " + e.getMessage());
        }

        // Sem "privacy" explícito: conta com modo privado travado pelo
        // administrador recusa qualquer valor que a API tente forçar, e o vídeo
        // já nasce restrito ao time pelo padrão da conta.
        JsonNode criado = transporte.request("https://api.vimeo.com/me/videos",
                Map.of("Authorization", "bearer " + token, "Accept", VERSAO),
                Map.of("upload", Map.of("approach", "tus", "size", tamanho), "name", titulo),
                120000, "Vimeo");

        String destino = criado.path("upload").path("upload_link").asText(null);
        String link = criado.path("link").asText(null);
        if (destino == null || destino.isBlank())
            throw new IntegrationException(
                    "O Vimeo aceitou o pedido mas não devolveu onde enviar o arquivo.");

        enviarArquivo(destino, token, arquivo);
        return link;
    }

    /** O PATCH do TUS: bytes crus, sem JSON no meio. */
    private void enviarArquivo(String destino, String token, Path arquivo) {
        HttpURLConnection conexao = null;
        try {
            conexao = (HttpURLConnection) URI.create(destino).toURL().openConnection();
            conexao.setRequestMethod("PATCH");
            conexao.setConnectTimeout(20000);
            conexao.setReadTimeout(15 * 60 * 1000);
            conexao.setDoOutput(true);
            conexao.setFixedLengthStreamingMode(Files.size(arquivo));
            conexao.setRequestProperty("Authorization", "bearer " + token);
            conexao.setRequestProperty("Tus-Resumable", "1.0.0");
            conexao.setRequestProperty("Upload-Offset", "0");
            conexao.setRequestProperty("Content-Type", "application/offset+octet-stream");

            try (OutputStream saida = conexao.getOutputStream()) {
                Files.copy(arquivo, saida);
            }

            int status = conexao.getResponseCode();
            if (status < 200 || status >= 300)
                throw new IntegrationException("O envio do vídeo ao Vimeo falhou (HTTP " + status + ").");
        } catch (IntegrationException e) {
            throw e;
        } catch (java.net.ProtocolException semPatch) {
            // Algumas JVMs recusam PATCH em HttpURLConnection; o override é o
            // caminho que o próprio Vimeo documenta para esse caso.
            enviarComOverride(destino, token, arquivo);
        } catch (Exception e) {
            throw new IntegrationException("Não foi possível enviar o vídeo ao Vimeo: " + e.getMessage());
        } finally {
            if (conexao != null) conexao.disconnect();
        }
    }

    private void enviarComOverride(String destino, String token, Path arquivo) {
        HttpURLConnection conexao = null;
        try {
            conexao = (HttpURLConnection) URI.create(destino).toURL().openConnection();
            conexao.setRequestMethod("POST");
            conexao.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conexao.setConnectTimeout(20000);
            conexao.setReadTimeout(15 * 60 * 1000);
            conexao.setDoOutput(true);
            conexao.setFixedLengthStreamingMode(Files.size(arquivo));
            conexao.setRequestProperty("Authorization", "bearer " + token);
            conexao.setRequestProperty("Tus-Resumable", "1.0.0");
            conexao.setRequestProperty("Upload-Offset", "0");
            conexao.setRequestProperty("Content-Type", "application/offset+octet-stream");

            try (OutputStream saida = conexao.getOutputStream()) {
                Files.copy(arquivo, saida);
            }

            int status = conexao.getResponseCode();
            if (status < 200 || status >= 300)
                throw new IntegrationException("O envio do vídeo ao Vimeo falhou (HTTP " + status + ").");
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("Não foi possível enviar o vídeo ao Vimeo: " + e.getMessage());
        } finally {
            if (conexao != null) conexao.disconnect();
        }
    }

    /**
     * @param exigir quando verdadeiro, a ausência do token vira erro explicado;
     *               quando falso, devolve vazio — é como a tela pergunta se o
     *               envio está disponível sem provocar exceção
     */
    private String token(boolean exigir) {
        String token;
        try {
            token = integracoes.secret("vimeo");
        } catch (RuntimeException naoConfigurado) {
            token = "";
        }
        if ((token == null || token.isBlank()) && exigir)
            throw new IntegrationException("O Vimeo não está configurado. Informe o token em "
                    + "Integrações para poder enviar o vídeo da falha.");
        return token == null ? "" : token;
    }
}
