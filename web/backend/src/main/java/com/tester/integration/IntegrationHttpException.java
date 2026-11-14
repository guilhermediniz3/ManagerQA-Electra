package com.tester.integration;

/**
 * Carrega o status e, quando o provider explica a recusa em campo próprio, o
 * motivo já resumido. Nunca o corpo bruto da resposta nem credenciais: só o
 * texto de `errorMessages`/`errors`, que é a explicação destinada ao usuário.
 */
public class IntegrationHttpException extends IntegrationException {
    private final int status;

    public IntegrationHttpException(String provider, int status) {
        this(provider, status, null);
    }

    public IntegrationHttpException(String provider, int status, String reason) {
        super(provider + " respondeu HTTP " + status + "." +
              (reason == null || reason.isBlank() ? " Verifique configuração e permissões." : " " + reason));
        this.status = status;
    }

    public int getStatus() { return status; }
}
