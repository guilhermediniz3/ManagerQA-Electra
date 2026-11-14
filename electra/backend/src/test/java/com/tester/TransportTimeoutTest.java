package com.tester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.IntegrationException;
import com.tester.integration.IntegrationTimeoutException;
import com.tester.integration.JsonHttpTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Demora e indisponibilidade são coisas diferentes.
 *
 * Quem usa a IA local precisa distinguir: se o serviço não terminou a tempo, a
 * saída é esperar ou trocar por um modelo menor; se não há nada escutando, é
 * ligar o serviço. As duas chegavam com a mesma frase.
 */
class TransportTimeoutTest {

    private final JsonHttpTransport transport = new JsonHttpTransport(new ObjectMapper());

    @Test
    void demoraViraFalhaDeTempo() throws Exception {
        try (var lento = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            var thread = new Thread(() -> {
                // Aceita a conexão e nunca responde: é o que um modelo grande
                // fazendo conta na CPU parece do lado de cá.
                try (var cliente = lento.accept()) {
                    Thread.sleep(30_000);
                } catch (Exception encerrado) {
                    // o teste termina antes; nada a fazer
                }
            });
            thread.setDaemon(true);
            thread.start();

            assertThatThrownBy(() -> transport.request(
                    "http://127.0.0.1:" + lento.getLocalPort(), Map.of(), null, 800, "IA local"))
                .isInstanceOf(IntegrationTimeoutException.class)
                .hasMessageContaining("demorou demais");
        }
    }

    @Test
    void portaFechadaContinuaSendoIndisponibilidade() throws IOException {
        int fechada;
        try (var porta = new ServerSocket(0)) { fechada = porta.getLocalPort(); }

        assertThatThrownBy(() -> transport.request(
                "http://127.0.0.1:" + fechada, Map.of(), null, 2000, "IA local"))
            .isInstanceOf(IntegrationException.class)
            .isNotInstanceOf(IntegrationTimeoutException.class)
            .hasMessageContaining("indisponível");
    }
}
