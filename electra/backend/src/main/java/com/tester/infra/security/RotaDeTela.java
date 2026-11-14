package com.tester.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Reconhece a navegação da interface, para deixá-la carregar sem sessão.
 *
 * O app desktop abre a URL do próprio backend, e recarregar uma tela como
 * /test-plans/3 pede ao servidor um caminho que só existe no React Router. Sem
 * liberar esses caminhos, a janela recebe o JSON de "autenticação necessária"
 * no lugar do sistema.
 *
 * A pergunta que se faz aqui é "existe um controlador para este caminho?", e
 * não "este caminho está numa lista de rotas públicas". A lista foi como o
 * problema nasceu: endpoints criados depois dela ficavam de fora e acabavam
 * abertos. Havendo controlador, é API e continua exigindo sessão; não havendo,
 * é rota de tela e o SpaForwardingConfig devolve o index.html.
 */
@Component
public class RotaDeTela implements RequestMatcher {

    private final ObjectProvider<RequestMappingHandlerMapping> controladores;

    public RotaDeTela(ObjectProvider<RequestMappingHandlerMapping> controladores) {
        this.controladores = controladores;
    }

    /**
     * Prefixos que pertencem à API, e nunca à navegação.
     *
     * Sem isto, um caminho de API escrito errado devolvia a página inteira com
     * 200 — e quem consome a API recebia HTML no lugar de um 404, sem entender
     * por quê. Rota de tela não vive sob estes prefixos.
     */
    public static final java.util.List<String> DA_API = java.util.List.of(
            "/api/", "/auth/", "/runmanager/", "/database/", "/testplans", "/test-suites",
            "/testcases", "/suite-templates", "/gherkin", "/regressions", "/doc-cards",
            "/developers", "/testers", "/technologies", "/modules", "/relatorios",
            "/relatorio-jira", "/actuator/");

    /**
     * Se o caminho pertence à API.
     *
     * É a mesma pergunta que o encaminhamento do SPA precisa fazer, e por isso
     * a resposta mora aqui: eram duas listas antes, e a do encaminhamento ficou
     * para trás — caminhos de API fora de {@code /api} chegavam ao navegador
     * como a página inteira, com 200.
     */
    public static boolean daApi(String caminho) {
        if (caminho == null) return false;
        String rota = caminho.startsWith("/") ? caminho : "/" + caminho;
        return DA_API.stream().anyMatch(prefixo ->
                rota.equals(prefixo) || rota.equals(prefixo.replaceAll("/$", ""))
                        || rota.startsWith(prefixo));
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        // Só navegação: um POST nunca é abertura de tela.
        if (!"GET".equals(request.getMethod())) return false;
        String caminho = request.getRequestURI();
        if (daApi(caminho)) return false;
        try {
            // Mais de um mapeamento responde por controladores — o da aplicação
            // e o do actuator. Basta um deles reconhecer o caminho para que ele
            // seja API, e não tela.
            return controladores.orderedStream().noneMatch(mapeamento -> {
                try {
                    return mapeamento.getHandler(request) != null;
                } catch (Exception naoDeuParaSaber) {
                    return true;
                }
            });
        } catch (Exception naoDeuParaSaber) {
            // Na dúvida, exige sessão: errar para o lado fechado custa uma tela
            // que não abre, e para o lado aberto custa um endpoint exposto.
            return false;
        }
    }
}
