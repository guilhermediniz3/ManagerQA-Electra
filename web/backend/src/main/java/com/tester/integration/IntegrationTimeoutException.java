package com.tester.integration;

/**
 * O provider aceitou a conexão mas não terminou de responder a tempo.
 *
 * Existe separada da falha genérica porque a saída é outra: aqui o serviço está
 * de pé e o que falta é tempo — ou um modelo menor —, enquanto a falha genérica
 * costuma significar que não há nada escutando.
 */
public class IntegrationTimeoutException extends IntegrationException {
    public IntegrationTimeoutException(String message) {
        super(message);
    }
}
