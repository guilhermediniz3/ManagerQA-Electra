package com.tester.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

/**
 * Backup, restauração e limpeza do banco.
 *
 * O dump é gerado aqui, em Java, e não pelo {@code pg_dump}: o Postgres que
 * acompanha o aplicativo foi recortado para caber no instalador e só traz
 * {@code initdb}, {@code pg_ctl} e {@code postgres}. Embarcar as ferramentas de
 * linha de comando só para isto engordaria o instalador, e ainda deixaria a
 * versão web dependente de ter o cliente do Postgres na máquina do servidor.
 *
 * O arquivo é SQL comum — apagar e inserir, na ordem das dependências —, o que
 * o torna legível, versionável e aplicável à mão se um dia for preciso. O que
 * ele não é: um backup físico do cluster. Não guarda usuários do banco,
 * permissões nem o esquema; serve para levar os dados de uma instalação para
 * outra do mesmo sistema, que é para o que foi pedido.
 */
@Service
public class DatabaseBackupService {

    private final DataSource dataSource;

    public DatabaseBackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** A primeira linha do arquivo; é por ela que a importação o reconhece. */
    public static final String ASSINATURA = "-- QA Manager · backup do banco de dados";

    /** Esquemas que não são do aplicativo e nunca entram no backup. */
    private static final Set<String> ESQUEMAS_DO_SISTEMA =
            Set.of("pg_catalog", "information_schema", "pg_toast");

    /**
     * O que "zerar" apaga: o trabalho registrado, não o cadastro.
     *
     * Ficam de fora usuários, integrações e as listas que alimentam os
     * seletores das telas — desenvolvedores, testers, tecnologias, módulos, os
     * templates de suíte e o cadastro do RunManager. Zerar para recomeçar um
     * ciclo de testes não deveria custar reconfigurar o Jira e recadastrar o
     * time.
     */
    private static final List<String> DADOS_DE_TRABALHO = List.of(
            "manager_demand",
            "tb_test_plan", "tb_test_suite", "tb_test_case", "tb_gherkin_feature",
            "tb_regression", "tb_regression_item",
            "tb_doc_card",
            "tb_run_execution", "tb_run_failure", "tb_run_project_snapshot", "tb_run_manual_case",
            "manager_audit_log");

    /** Uma tabela e quantas linhas ela tem, para a tela dizer o que está em jogo. */
    public record Contagem(String tabela, long linhas, boolean apagadaAoZerar) {}

    /** Uma tabela do banco, com esquema, para montar o nome completo. */
    private record Tabela(String esquema, String nome) {
        String completo() {
            return esquema == null || esquema.isBlank() ? aspas(nome) : aspas(esquema) + "." + aspas(nome);
        }

        static String aspas(String identificador) {
            return "\"" + identificador.replace("\"", "\"\"") + "\"";
        }
    }

    // ── Resumo ───────────────────────────────────────────────────────────────

    /** Quantas linhas há em cada tabela, e quais o "zerar" levaria. */
    public List<Contagem> resumo() {
        try (Connection conexao = dataSource.getConnection()) {
            List<Contagem> contagens = new ArrayList<>();
            for (Tabela tabela : tabelas(conexao)) {
                long linhas = contar(conexao, tabela);
                contagens.add(new Contagem(tabela.nome(), linhas, DADOS_DE_TRABALHO.contains(tabela.nome())));
            }
            contagens.sort((a, b) -> a.tabela().compareToIgnoreCase(b.tabela()));
            return contagens;
        } catch (SQLException falha) {
            throw new IllegalStateException("Não foi possível ler o banco: " + falha.getMessage(), falha);
        }
    }

    private long contar(Connection conexao, Tabela tabela) throws SQLException {
        try (Statement consulta = conexao.createStatement();
             ResultSet resultado = consulta.executeQuery("SELECT COUNT(*) FROM " + tabela.completo())) {
            return resultado.next() ? resultado.getLong(1) : 0;
        }
    }

    // ── Exportar ─────────────────────────────────────────────────────────────

    /** O banco inteiro em SQL: apaga tudo e insere de volta, na ordem certa. */
    public String exportar() {
        try (Connection conexao = dataSource.getConnection()) {
            List<Tabela> ordenadas = emOrdemDeDependencia(conexao, tabelas(conexao));
            StringBuilder sql = new StringBuilder();
            sql.append(ASSINATURA).append("\n");
            sql.append("-- gerado em ")
               .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
               .append("\n");
            sql.append("-- Restaura o estado deste banco: apaga o conteúdo das tabelas abaixo e\n");
            sql.append("-- insere estas linhas. Não cria o esquema — a aplicação já o cria ao subir.\n\n");
            sql.append("BEGIN;\n\n");

            // Apagar na ordem inversa da dependência: o filho sai antes do pai.
            sql.append("-- Limpeza\n");
            for (int i = ordenadas.size() - 1; i >= 0; i--) {
                sql.append("DELETE FROM ").append(ordenadas.get(i).completo()).append(";\n");
            }
            sql.append("\n");

            for (Tabela tabela : ordenadas) escreverLinhas(conexao, tabela, sql);

            sql.append("COMMIT;\n");
            return sql.toString();
        } catch (SQLException falha) {
            throw new IllegalStateException("Não foi possível exportar o banco: " + falha.getMessage(), falha);
        }
    }

    private void escreverLinhas(Connection conexao, Tabela tabela, StringBuilder sql) throws SQLException {
        try (Statement consulta = conexao.createStatement();
             ResultSet linhas = consulta.executeQuery("SELECT * FROM " + tabela.completo())) {
            ResultSetMetaData colunas = linhas.getMetaData();
            int quantas = colunas.getColumnCount();

            StringBuilder nomes = new StringBuilder();
            for (int i = 1; i <= quantas; i++) {
                if (i > 1) nomes.append(", ");
                nomes.append(Tabela.aspas(colunas.getColumnName(i)));
            }

            boolean primeira = true;
            while (linhas.next()) {
                if (primeira) {
                    sql.append("-- ").append(tabela.nome()).append("\n");
                    primeira = false;
                }
                sql.append("INSERT INTO ").append(tabela.completo()).append(" (").append(nomes).append(") VALUES (");
                for (int i = 1; i <= quantas; i++) {
                    if (i > 1) sql.append(", ");
                    sql.append(literal(linhas, i, colunas.getColumnType(i)));
                }
                sql.append(");\n");
            }
            if (!primeira) sql.append("\n");
        }
    }

    /**
     * O valor como ele entra no SQL.
     *
     * Texto vai entre aspas simples com as aspas internas dobradas, que é o
     * escape do padrão e não depende de configuração do servidor. Data e hora
     * saem no formato ISO, também entre aspas: o Postgres converte, e o arquivo
     * continua legível.
     */
    private String literal(ResultSet linha, int coluna, int tipo) throws SQLException {
        Object valor = linha.getObject(coluna);
        if (valor == null || linha.wasNull()) return "NULL";

        return switch (tipo) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT,
                 Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL ->
                    valor.toString();
            case Types.BOOLEAN, Types.BIT -> Boolean.TRUE.equals(linha.getBoolean(coluna)) ? "TRUE" : "FALSE";
            default -> texto(String.valueOf(valor));
        };
    }

    private String texto(String valor) {
        return "'" + valor.replace("'", "''") + "'";
    }

    // ── Importar ─────────────────────────────────────────────────────────────

    /**
     * Aplica um arquivo gerado pela exportação.
     *
     * Só entra arquivo com a assinatura: executar SQL qualquer que chegue pela
     * tela seria dar a quem tem uma sessão o poder de rodar o que quiser no
     * banco. Tudo vai numa transação — arquivo pela metade deixaria o sistema
     * num estado que ninguém sabe descrever.
     */
    public int importar(String arquivo) {
        if (arquivo == null || arquivo.isBlank())
            throw new IllegalArgumentException("Arquivo vazio.");
        if (!arquivo.stripLeading().startsWith(ASSINATURA))
            throw new IllegalArgumentException(
                    "Este arquivo não é um backup do QA Manager. Use um arquivo gerado pelo botão Exportar.");

        List<String> comandos = comandos(arquivo);
        if (comandos.isEmpty()) throw new IllegalArgumentException("O arquivo não tem nenhum comando.");

        try (Connection conexao = dataSource.getConnection()) {
            boolean commitAutomatico = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            try (Statement execucao = conexao.createStatement()) {
                for (String comando : comandos) execucao.execute(comando);
                conexao.commit();
            } catch (SQLException falha) {
                conexao.rollback();
                throw new IllegalStateException(
                        "O banco recusou o arquivo e nada foi alterado: " + falha.getMessage(), falha);
            } finally {
                conexao.setAutoCommit(commitAutomatico);
            }
            ajustarSequencias(conexao);
            return comandos.size();
        } catch (SQLException falha) {
            throw new IllegalStateException("Não foi possível importar: " + falha.getMessage(), falha);
        }
    }

    /**
     * Quebra o arquivo em comandos.
     *
     * O ponto e vírgula só encerra um comando quando está fora de aspas: um
     * cenário de teste com "faturar; emitir" no meio do texto partiria o INSERT
     * em dois pedaços inválidos.
     */
    public static List<String> comandos(String arquivo) {
        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeTexto = false;

        for (int i = 0; i < arquivo.length(); i++) {
            char caractere = arquivo.charAt(i);

            if (!dentroDeTexto && caractere == '-' && i + 1 < arquivo.length() && arquivo.charAt(i + 1) == '-'
                    && atual.toString().isBlank()) {
                while (i < arquivo.length() && arquivo.charAt(i) != '\n') i++;
                continue;
            }
            if (caractere == '\'') {
                // Duas aspas seguidas são uma aspa dentro do texto, não o fim dele.
                if (dentroDeTexto && i + 1 < arquivo.length() && arquivo.charAt(i + 1) == '\'') {
                    atual.append("''");
                    i++;
                    continue;
                }
                dentroDeTexto = !dentroDeTexto;
            }
            if (caractere == ';' && !dentroDeTexto) {
                adicionar(comandos, atual.toString());
                atual.setLength(0);
                continue;
            }
            atual.append(caractere);
        }
        adicionar(comandos, atual.toString());
        return comandos;
    }

    private static void adicionar(List<String> comandos, String comando) {
        String limpo = comando.strip();
        if (limpo.isEmpty()) return;
        // BEGIN e COMMIT do arquivo são ignorados: a transação é desta conexão,
        // e abrir outra no meio dela deixaria metade fora do que se desfaz.
        if (limpo.equalsIgnoreCase("BEGIN") || limpo.equalsIgnoreCase("COMMIT")
                || limpo.equalsIgnoreCase("ROLLBACK")) return;
        comandos.add(limpo);
    }

    // ── Zerar ────────────────────────────────────────────────────────────────

    /** Apaga o trabalho registrado e devolve quantas linhas saíram de cada tabela. */
    public Map<String, Long> zerar() {
        return apagar(false);
    }

    /**
     * Apaga o banco inteiro, inclusive contas e credenciais.
     *
     * Depois disto o sistema fica como recém-instalado, e quem clicou perde a
     * própria sessão junto — a conta que a sustentava deixou de existir. Não é
     * um beco sem saída: o seed de administrador recria a conta padrão na
     * próxima vez que o aplicativo subir. Ainda assim, é a operação da qual só
     * um backup traz de volta, e por isso a tela cobra a confirmação por
     * extenso antes de chegar aqui.
     */
    public Map<String, Long> apagarTudo() {
        return apagar(true);
    }

    private Map<String, Long> apagar(boolean tudo) {
        try (Connection conexao = dataSource.getConnection()) {
            List<Tabela> existentes = new ArrayList<>();
            for (Tabela tabela : tabelas(conexao))
                if (tudo || DADOS_DE_TRABALHO.contains(tabela.nome())) existentes.add(tabela);

            List<Tabela> ordenadas = emOrdemDeDependencia(conexao, existentes);
            Map<String, Long> apagadas = new LinkedHashMap<>();

            boolean commitAutomatico = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            try (Statement execucao = conexao.createStatement()) {
                for (int i = ordenadas.size() - 1; i >= 0; i--) {
                    Tabela tabela = ordenadas.get(i);
                    int linhas = execucao.executeUpdate("DELETE FROM " + tabela.completo());
                    apagadas.put(tabela.nome(), (long) linhas);
                }
                conexao.commit();
            } catch (SQLException falha) {
                conexao.rollback();
                throw new IllegalStateException(
                        "Nada foi apagado: o banco recusou a operação. " + falha.getMessage(), falha);
            } finally {
                conexao.setAutoCommit(commitAutomatico);
            }
            return apagadas;
        } catch (SQLException falha) {
            throw new IllegalStateException("Não foi possível apagar: " + falha.getMessage(), falha);
        }
    }

    // ── Apoio ────────────────────────────────────────────────────────────────

    /** As tabelas do aplicativo, em qualquer esquema que não seja do sistema. */
    private List<Tabela> tabelas(Connection conexao) throws SQLException {
        DatabaseMetaData metadados = conexao.getMetaData();
        List<Tabela> tabelas = new ArrayList<>();
        try (ResultSet encontradas = metadados.getTables(conexao.getCatalog(), null, "%",
                new String[]{"TABLE"})) {
            while (encontradas.next()) {
                String esquema = encontradas.getString("TABLE_SCHEM");
                String nome = encontradas.getString("TABLE_NAME");
                if (esquema != null && ESQUEMAS_DO_SISTEMA.contains(esquema.toLowerCase())) continue;
                tabelas.add(new Tabela(esquema, nome));
            }
        }
        return tabelas;
    }

    /**
     * Pais antes de filhos.
     *
     * Sem isso, inserir um caso de teste antes da suíte dele — ou apagar a
     * suíte antes do caso — esbarra na chave estrangeira. Ciclo entre tabelas
     * não trava a ordenação: o que sobra entra no fim, e se um dia existir um
     * ciclo de verdade o arquivo falhará na importação em vez de sair errado
     * em silêncio.
     */
    private List<Tabela> emOrdemDeDependencia(Connection conexao, List<Tabela> tabelas) throws SQLException {
        DatabaseMetaData metadados = conexao.getMetaData();
        Map<String, Tabela> porNome = new LinkedHashMap<>();
        for (Tabela tabela : tabelas) porNome.put(tabela.nome().toLowerCase(), tabela);

        Map<String, Set<String>> dependeDe = new LinkedHashMap<>();
        for (Tabela tabela : tabelas) {
            Set<String> pais = new LinkedHashSet<>();
            try (ResultSet chaves = metadados.getImportedKeys(conexao.getCatalog(),
                    tabela.esquema(), tabela.nome())) {
                while (chaves.next()) {
                    String pai = chaves.getString("PKTABLE_NAME");
                    if (pai == null) continue;
                    String chave = pai.toLowerCase();
                    // Autorreferência não é dependência entre tabelas.
                    if (porNome.containsKey(chave) && !chave.equals(tabela.nome().toLowerCase())) pais.add(chave);
                }
            }
            dependeDe.put(tabela.nome().toLowerCase(), pais);
        }

        List<Tabela> ordenadas = new ArrayList<>();
        Set<String> colocadas = new HashSet<>();
        boolean avancou = true;
        while (colocadas.size() < porNome.size() && avancou) {
            avancou = false;
            for (Map.Entry<String, Tabela> entrada : porNome.entrySet()) {
                if (colocadas.contains(entrada.getKey())) continue;
                if (!colocadas.containsAll(dependeDe.get(entrada.getKey()))) continue;
                ordenadas.add(entrada.getValue());
                colocadas.add(entrada.getKey());
                avancou = true;
            }
        }
        for (Map.Entry<String, Tabela> entrada : porNome.entrySet())
            if (!colocadas.contains(entrada.getKey())) ordenadas.add(entrada.getValue());
        return ordenadas;
    }

    /**
     * Devolve cada sequência ao topo da sua tabela.
     *
     * Sem isso, o primeiro registro criado depois de uma importação tentaria um
     * id que já existe e o salvamento falharia — o dado estaria lá, e o sistema
     * pareceria quebrado. O comando é do PostgreSQL; no banco em memória dos
     * testes não há o que ajustar, e por isso ele é pulado em vez de escrito no
     * arquivo, que assim continua aplicável em qualquer lugar.
     */
    private void ajustarSequencias(Connection conexao) throws SQLException {
        if (!conexao.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;

        for (Tabela tabela : tabelas(conexao)) {
            for (String coluna : chavesPrimarias(conexao, tabela)) {
                String sql = """
                        SELECT setval(sequencia, COALESCE(maior, 1), maior IS NOT NULL)
                        FROM (SELECT pg_get_serial_sequence(?, ?) AS sequencia,
                                     (SELECT MAX(%s) FROM %s) AS maior) AS dados
                        WHERE sequencia IS NOT NULL
                        """.formatted(Tabela.aspas(coluna), tabela.completo());
                try (PreparedStatement ajuste = conexao.prepareStatement(sql)) {
                    ajuste.setString(1, tabela.completo());
                    ajuste.setString(2, coluna);
                    ajuste.execute();
                } catch (SQLException naoEhSerial) {
                    // Chave que não vem de sequência não tem o que ajustar.
                }
            }
        }
    }

    private List<String> chavesPrimarias(Connection conexao, Tabela tabela) throws SQLException {
        List<String> colunas = new ArrayList<>();
        try (ResultSet chaves = conexao.getMetaData().getPrimaryKeys(conexao.getCatalog(),
                tabela.esquema(), tabela.nome())) {
            while (chaves.next()) colunas.add(chaves.getString("COLUMN_NAME"));
        }
        return colunas;
    }
}
