package com.tester.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tester.entity.User;
import com.tester.repository.UserRepository;

/**
 * Garante que sempre exista uma conta conhecida para entrar.
 *
 * Numa instalação nova — ou num banco restaurado de um backup vazio — o sistema
 * subiria sem nenhuma conta, e a primeira coisa que a pessoa veria seria uma
 * tela de login sem ter onde pegar a senha. Esta conta é o ponto de partida.
 *
 * O e-mail e a senha vêm do ambiente e caem no padrão versionado quando não
 * estiverem preenchidos, como todo valor configurável deste projeto. Antes de
 * publicar o código num repositório aberto, troque o padrão: senha escrita no
 * fonte é senha conhecida por quem lê o fonte.
 */
@Component
public class SeedDeAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDeAdministrador.class);

    private final UserRepository users;
    private final PasswordEncoder senhas;
    private final TransactionTemplate transacoes;

    @Value("${manager.seed.admin.email:}")
    private String email;

    @Value("${manager.seed.admin.password:}")
    private String senha;

    @Value("${manager.seed.admin.name:Administrador}")
    private String nome;

    /**
     * Redefine a senha desta conta na próxima subida.
     *
     * É a saída para quem perdeu o acesso: quem alcança o arquivo de
     * configuração da instalação já tem a máquina nas mãos, e a alternativa
     * seria editar a tabela de usuários à mão. Fora desse caso fica desligado,
     * porque senão o seed desfaria toda troca de senha no boot seguinte.
     */
    @Value("${manager.seed.admin.reset:false}")
    private boolean redefinirSenha;

    public SeedDeAdministrador(UserRepository users, PasswordEncoder senhas,
            PlatformTransactionManager transacoes) {
        this.users = users;
        this.senhas = senhas;
        this.transacoes = new TransactionTemplate(transacoes);
    }

    /**
     * Falha aqui não derruba o sistema.
     *
     * Um runner que lança encerra a aplicação inteira, e foi o que aconteceu
     * numa atualização em que a migração do esquema não passou: o erro real
     * era o banco, mas o que a pessoa viu foi o aplicativo não abrindo.
     */
    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            // A transação vem do template, e não de uma anotação: chamada de um
            // método da própria classe não passa pelo proxy do Spring.
            transacoes.executeWithoutResult(estado -> garantirConta());
        } catch (RuntimeException falha) {
            log.error("Não foi possível garantir a conta padrão: {}", falha.getMessage());
        }
    }

    private void garantirConta() {
        if (email == null || email.isBlank() || senha == null || senha.isEmpty()) {
            log.info("Conta inicial opcional não configurada; use Criar conta local ou SEED_ADMIN_EMAIL/SEED_ADMIN_PASSWORD.");
            return;
        }
        User conta = users.findByEmail(email).orElse(null);
        if (conta == null) {
            users.save(new User(nome, email, senhas.encode(senha), true));
            log.info("Conta padrão criada: {}", email);
            return;
        }
        boolean mudou = false;
        if (!conta.isActive()) {
            conta.setActive(true);
            mudou = true;
        }
        if (redefinirSenha) {
            conta.setPassword(senhas.encode(senha));
            mudou = true;
            log.warn("Senha da conta padrão redefinida pela configuração; desligue manager.seed.admin.reset.");
        }
        if (mudou) users.save(conta);
    }
}
