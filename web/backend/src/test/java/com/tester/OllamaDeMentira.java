package com.tester;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * Um Ollama de mentira para os testes: responde o que o serviço de verdade
 * responderia e anota o que recebeu.
 *
 * É um ServerSocket cru, e não o HttpServer do JDK, porque este último abre um
 * Selector — que não sobe em máquina onde o loopback interno do Java está
 * bloqueado, como acontece com alguns antivírus.
 */
class OllamaDeMentira implements Closeable {
    private static final String FIM_DO_CABECALHO = "\r\n\r\n";

    private final ServerSocket socket =
        new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
    private final Function<String, String> resposta;

    final List<String> caminhos = Collections.synchronizedList(new ArrayList<>());
    final List<String> autorizacoes = Collections.synchronizedList(new ArrayList<>());
    final List<String> corpos = Collections.synchronizedList(new ArrayList<>());

    /** Recebe o caminho pedido e devolve o JSON de resposta. */
    OllamaDeMentira(Function<String, String> resposta) throws IOException {
        this.resposta = resposta;
        var thread = new Thread(this::atender, "ollama-de-mentira");
        thread.setDaemon(true);
        thread.start();
    }

    String endereco() { return "http://127.0.0.1:" + socket.getLocalPort(); }

    private void atender() {
        while (!socket.isClosed()) {
            try (var cliente = socket.accept()) {
                var entrada = cliente.getInputStream();

                // Cabeçalho e corpo são lidos como bytes, e não por um Reader: o
                // Content-Length conta bytes, e deixar corpo por ler faz o
                // cliente repetir o POST inteiro.
                var cabecalho = new StringBuilder();
                int lido;
                while (!cabecalho.toString().endsWith(FIM_DO_CABECALHO)
                       && (lido = entrada.read()) >= 0)
                    cabecalho.append((char) lido);
                if (cabecalho.length() == 0) continue;

                var linhas = cabecalho.toString().split("\r\n");
                String caminho = linhas[0].split(" ")[1];
                caminhos.add(caminho);

                String autorizacao = null;
                int tamanho = 0;
                for (var linha : linhas) {
                    if (linha.regionMatches(true, 0, "Authorization:", 0, 14))
                        autorizacao = linha.substring(14).trim();
                    if (linha.regionMatches(true, 0, "Content-Length:", 0, 15))
                        tamanho = Integer.parseInt(linha.substring(15).trim());
                }
                autorizacoes.add(autorizacao);
                corpos.add(new String(entrada.readNBytes(tamanho), StandardCharsets.UTF_8));

                byte[] bytes = resposta.apply(caminho).getBytes(StandardCharsets.UTF_8);
                var saida = cliente.getOutputStream();
                saida.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close" + FIM_DO_CABECALHO)
                    .getBytes(StandardCharsets.US_ASCII));
                saida.write(bytes);
                saida.flush();
            } catch (IOException encerrado) {
                return;
            }
        }
    }

    @Override public void close() throws IOException { socket.close(); }
}
