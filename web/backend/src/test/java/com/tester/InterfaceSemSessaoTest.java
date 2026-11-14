package com.tester;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A casca da interface é pública; o conteúdo, não.
 *
 * O app desktop abre a URL do próprio backend ao iniciar, e a tela de login só
 * pode ser preenchida depois que a página carrega. Com tudo exigindo
 * autenticação, a janela recebia o JSON de "autenticação necessária" no lugar
 * do sistema — e ninguém conseguia nem chegar ao login.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:interface_publica;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class InterfaceSemSessaoTest {

    @Autowired MockMvc mvc;

    @Test
    void aPaginaAbreSemSessao() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    /**
     * Rota de tela recarregada cai no index.html: quem aperta F5 em
     * /test-plans/3 tem que continuar no sistema, e não ver um 404.
     */
    @Test
    void rotaDeTelaCaiNaPagina() throws Exception {
        mvc.perform(get("/test-plans/3")).andExpect(status().isOk());
        mvc.perform(get("/regressao")).andExpect(status().isOk());
    }

    /** Toda API continua fechada, inclusive as criadas depois desta regra. */
    @Test
    void asApisContinuamExigindoSessao() throws Exception {
        for (String rota : new String[]{
                "/api/ai/local", "/api/integrations", "/api/users", "/regressions",
                "/doc-cards", "/testplans", "/test-suites", "/suite-templates",
                "/testcases/1", "/modules", "/testers"}) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        }
    }

    /**
     * Caminho de API que não existe não vira página.
     *
     * Devolver o index.html no lugar de um erro faz endpoint errado parecer
     * sucesso para quem integra — e foi o que acontecia com caminhos fora de
     * {@code /api}, como um {@code /runmanager/} inexistente, que chegavam ao
     * navegador com 200 e HTML.
     *
     * Sem sessão a resposta é 401, e não 404: dizer a um anônimo quais caminhos
     * existem é descrever a superfície do sistema para quem ainda não provou
     * quem é. Com sessão, aí sim, 404.
     */
    @Test
    void apiInexistenteNaoViraPagina() throws Exception {
        mvc.perform(get("/api/nao-existe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/runmanager/nao-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiInexistenteComSessaoDevolveNaoEncontrado() throws Exception {
        var conta = new com.tester.entity.User("QA Rota", "rota-" + System.nanoTime() + "@example.test",
                senhas.encode("Rota-123456"), true);
        users.save(conta);
        String autorizacao = "Bearer " + tokens.generationToken(conta);

        mvc.perform(get("/api/nao-existe").header("Authorization", autorizacao))
                .andExpect(status().isNotFound());
        mvc.perform(get("/runmanager/nao-existe").header("Authorization", autorizacao))
                .andExpect(status().isNotFound());
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.security.crypto.password.PasswordEncoder senhas;
    @org.springframework.beans.factory.annotation.Autowired
    com.tester.infra.security.TokenService tokens;
    @org.springframework.beans.factory.annotation.Autowired
    com.tester.repository.UserRepository users;
}
