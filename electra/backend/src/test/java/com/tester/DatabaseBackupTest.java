package com.tester;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tester.service.DatabaseBackupService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backup, restauração e limpeza do banco.
 *
 * O banco aqui é o H2 em modo PostgreSQL, com as tabelas de verdade recriadas à
 * mão — é o que o projeto tem. Dois pontos ficam fora do alcance destes testes
 * e precisam da conferência contra o PostgreSQL real: o ajuste das sequências
 * depois da importação, que usa função do Postgres e por isso é pulado no H2, e
 * o comportamento com o esquema {@code operational}.
 */
class DatabaseBackupTest {

    /** O banco de cada teste é só dele: um nome novo evita herdar linha de outro. */
    private final String url = "jdbc:h2:mem:backup-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private final DataSource fonte = new DataSource() {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
        @Override public Connection getConnection(String u, String s) throws SQLException { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> tipo) { return null; }
        @Override public boolean isWrapperFor(Class<?> tipo) { return false; }
    };

    private final DatabaseBackupService service = new DatabaseBackupService(fonte);

    @BeforeEach
    void criarEsquema() throws SQLException {
        executar("""
                CREATE TABLE tb_module (id BIGINT PRIMARY KEY, name VARCHAR(120));
                CREATE TABLE tb_user (id BIGINT PRIMARY KEY, email VARCHAR(120));
                CREATE TABLE tb_test_plan (id BIGINT PRIMARY KEY, name VARCHAR(255),
                        module_id BIGINT REFERENCES tb_module(id));
                CREATE TABLE tb_test_suite (id BIGINT PRIMARY KEY,
                        test_plan_id BIGINT REFERENCES tb_test_plan(id));
                CREATE TABLE tb_test_case (id BIGINT PRIMARY KEY, scenario VARCHAR(500),
                        concluido BOOLEAN, test_suite_id BIGINT REFERENCES tb_test_suite(id));
                """);
    }

    private void executar(String sql) throws SQLException {
        try (Connection conexao = fonte.getConnection(); Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private long contar(String tabela) throws SQLException {
        try (Connection conexao = fonte.getConnection();
             Statement consulta = conexao.createStatement();
             ResultSet resultado = consulta.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            return resultado.next() ? resultado.getLong(1) : -1;
        }
    }

    private void popular() throws SQLException {
        executar("""
                INSERT INTO tb_module VALUES (1, 'Fiscal');
                INSERT INTO tb_user VALUES (1, 'qa@exemplo.test');
                INSERT INTO tb_test_plan VALUES (10, 'Retirar ICMS', 1);
                INSERT INTO tb_test_suite VALUES (100, 10);
                INSERT INTO tb_test_case VALUES (1000, 'Emitir nota', TRUE, 100);
                """);
    }

    // ── Exportar ─────────────────────────────────────────────────────────────

    @Test void exportBringsEveryRowBackAsInsert() throws SQLException {
        popular();

        String sql = service.exportar();

        assertThat(sql).startsWith("-- QA Manager");
        // O nome vem com o esquema, como o banco o reporta.
        assertThat(sql)
                .containsIgnoringCase("INSERT INTO \"public\".\"tb_module\"")
                .containsIgnoringCase("'Fiscal'")
                .containsIgnoringCase("'Emitir nota'")
                .containsIgnoringCase("'qa@exemplo.test'");
    }

    /**
     * Pais antes de filhos.
     *
     * Inserir um caso de teste antes da suíte dele esbarra na chave
     * estrangeira, e o arquivo inteiro morre na primeira linha.
     */
    @Test void parentsAreInsertedBeforeChildren() throws SQLException {
        popular();

        String sql = service.exportar().toLowerCase();

        assertThat(sql.indexOf("insert into \"public\".\"tb_module\""))
                .isLessThan(sql.indexOf("insert into \"public\".\"tb_test_plan\""));
        assertThat(sql.indexOf("insert into \"public\".\"tb_test_plan\""))
                .isLessThan(sql.indexOf("insert into \"public\".\"tb_test_suite\""));
        assertThat(sql.indexOf("insert into \"public\".\"tb_test_suite\""))
                .isLessThan(sql.indexOf("insert into \"public\".\"tb_test_case\""));
        // E a limpeza vem antes de tudo, na ordem contrária.
        assertThat(sql.indexOf("delete from \"public\".\"tb_test_case\""))
                .isLessThan(sql.indexOf("delete from \"public\".\"tb_module\""));
    }

    /** Aspas no texto do QA não podem partir o comando em dois. */
    @Test void aQuoteInsideTheTextIsEscaped() throws SQLException {
        popular();
        executar("INSERT INTO tb_test_case VALUES (1001, 'Emitir a nota do ''cliente''', FALSE, 100)");

        String sql = service.exportar();
        assertThat(sql).contains("'Emitir a nota do ''cliente'''");

        // E a prova real: o arquivo volta inteiro.
        service.importar(sql);
        assertThat(contar("tb_test_case")).isEqualTo(2);
    }

    @Test void nullAndBooleanAreWrittenAsSql() throws SQLException {
        executar("INSERT INTO tb_module VALUES (2, NULL)");

        String sql = service.exportar();
        assertThat(sql).contains("NULL");
        assertThat(sql).doesNotContain("'null'");
    }

    // ── Importar ─────────────────────────────────────────────────────────────

    /** O caminho inteiro: exportar, perder tudo, e voltar ao mesmo estado. */
    @Test void aBackupBringsTheDatabaseBackToWhereItWas() throws SQLException {
        popular();
        String backup = service.exportar();

        executar("DELETE FROM tb_test_case; DELETE FROM tb_test_suite; DELETE FROM tb_test_plan;"
                + " DELETE FROM tb_user; DELETE FROM tb_module;");
        assertThat(contar("tb_test_case")).isZero();

        service.importar(backup);

        assertThat(contar("tb_module")).isEqualTo(1);
        assertThat(contar("tb_test_plan")).isEqualTo(1);
        assertThat(contar("tb_test_case")).isEqualTo(1);
        assertThat(contar("tb_user")).isEqualTo(1);
    }

    /** Importar por cima substitui: o que não está no arquivo não sobrevive. */
    @Test void importReplacesWhatIsThereNow() throws SQLException {
        popular();
        String backup = service.exportar();
        executar("INSERT INTO tb_test_case VALUES (1002, 'Caso criado depois', FALSE, 100)");
        assertThat(contar("tb_test_case")).isEqualTo(2);

        service.importar(backup);

        assertThat(contar("tb_test_case")).isEqualTo(1);
    }

    /**
     * Arquivo que não é nosso não entra. Executar SQL qualquer que chegue pela
     * tela daria a quem tem uma sessão o poder de rodar o que quiser no banco.
     */
    @Test void aForeignFileIsRefused() {
        assertThatThrownBy(() -> service.importar("DROP TABLE tb_user;"))
                .hasMessageContaining("não é um backup do QA Manager");
        assertThatThrownBy(() -> service.importar("   "))
                .hasMessageContaining("vazio");
    }

    /** Arquivo pela metade não pode deixar o banco pela metade. */
    @Test void aBrokenFileChangesNothing() throws SQLException {
        popular();
        String quebrado = DatabaseBackupService.ASSINATURA + "\n"
                + "DELETE FROM tb_test_case;\n"
                + "INSERT INTO tb_test_case VALUES (1, 'ok', TRUE, 100);\n"
                + "INSERT INTO tabela_que_nao_existe VALUES (1);\n";

        assertThatThrownBy(() -> service.importar(quebrado))
                .hasMessageContaining("nada foi alterado");

        // O caso original continua lá: o rollback desfez o DELETE também.
        assertThat(contar("tb_test_case")).isEqualTo(1);
    }

    /** Ponto e vírgula dentro do texto não encerra o comando. */
    @Test void aSemicolonInsideTheTextDoesNotSplitTheCommand() {
        List<String> comandos = DatabaseBackupService.comandos(
                DatabaseBackupService.ASSINATURA + "\n"
                + "INSERT INTO tb_test_case VALUES (1, 'faturar; emitir', TRUE, 100);\n"
                + "INSERT INTO tb_module VALUES (9, 'Fiscal');\n");

        assertThat(comandos).hasSize(2);
        assertThat(comandos.get(0)).contains("faturar; emitir");
    }

    // ── Zerar ────────────────────────────────────────────────────────────────

    /**
     * Zerar leva o trabalho e deixa o cadastro. Recomeçar um ciclo de testes não
     * pode custar reconfigurar o Jira e recadastrar o time.
     */
    @Test void resetTakesTheWorkAndKeepsTheRegistry() throws SQLException {
        popular();

        Map<String, Long> apagadas = service.zerar();

        assertThat(contar("tb_test_case")).isZero();
        assertThat(contar("tb_test_suite")).isZero();
        assertThat(contar("tb_test_plan")).isZero();
        // Cadastro e usuários ficam.
        assertThat(contar("tb_module")).isEqualTo(1);
        assertThat(contar("tb_user")).isEqualTo(1);
        assertThat(apagadas).containsEntry("tb_test_case", 1L);
        assertThat(apagadas).doesNotContainKey("tb_user");
    }

    /**
     * Apagar tudo não poupa nada — nem as contas, nem as credenciais.
     *
     * É a diferença entre as duas operações da tela, e a que precisa ficar
     * provada: uma recomeça o ciclo de testes, a outra devolve o sistema ao
     * estado de recém-instalado.
     */
    @Test void wipeLeavesNothingBehind() throws SQLException {
        popular();

        Map<String, Long> apagadas = service.apagarTudo();

        for (String tabela : List.of("tb_test_case", "tb_test_suite", "tb_test_plan",
                                     "tb_module", "tb_user")) {
            assertThat(contar(tabela)).describedAs(tabela).isZero();
        }
        assertThat(apagadas).containsEntry("tb_user", 1L).containsEntry("tb_module", 1L);
    }

    /** Uma não é a outra: zerar preserva justamente o que apagar tudo leva. */
    @Test void resetAndWipeDifferInWhatTheyKeep() throws SQLException {
        popular();
        service.zerar();
        assertThat(contar("tb_user")).isEqualTo(1);
        assertThat(contar("tb_module")).isEqualTo(1);

        service.apagarTudo();
        assertThat(contar("tb_user")).isZero();
        assertThat(contar("tb_module")).isZero();
    }

    /** O resumo diz o que existe e o que o zerar levaria — é o que a tela mostra. */
    @Test void theSummarySaysWhatIsAtStake() throws SQLException {
        popular();

        List<DatabaseBackupService.Contagem> resumo = service.resumo();

        assertThat(resumo).anySatisfy(linha -> {
            assertThat(linha.tabela()).isEqualTo("tb_test_case");
            assertThat(linha.linhas()).isEqualTo(1);
            assertThat(linha.apagadaAoZerar()).isTrue();
        });
        assertThat(resumo).anySatisfy(linha -> {
            assertThat(linha.tabela()).isEqualTo("tb_user");
            assertThat(linha.apagadaAoZerar()).isFalse();
        });
    }
}
