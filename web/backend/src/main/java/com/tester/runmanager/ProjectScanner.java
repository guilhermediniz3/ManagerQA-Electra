package com.tester.runmanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/**
 * Lê o projeto de automação e descobre o que há para rodar.
 *
 * Não executa o Playwright: lê os arquivos-fonte. Ler o código é o que permite
 * saber quais casos existem sem esperar uma execução de vinte minutos, e é o
 * mesmo caminho que o painel da nuvem usa — por isso o formato reconhecido aqui
 * é igual ao de lá:
 *
 * <pre>
 *   tests/&lt;versão&gt;/&lt;módulo&gt;/&lt;arquivo&gt;.spec.ts
 *   test.describe('Nome @tag1 @tag2', () =&gt; {
 *     test('CT-001: Título do caso', async ({ page }) =&gt; {
 * </pre>
 *
 * O que não segue esse formato simplesmente não aparece — é melhor mostrar
 * menos do que inventar módulo a partir de pasta que não é módulo.
 */
@Component
public class ProjectScanner {

    /** Pastas que não são módulo de teste de ninguém. */
    private static final Set<String> IGNORADAS = Set.of("setup", "feature", "node_modules");

    private static final Pattern TAG = Pattern.compile("@[\\w-]+");
    private static final Pattern DESCRIBE =
            Pattern.compile("test\\.describe(?:\\.\\w+)?\\s*\\(\\s*(['\"`])(.*?)\\1");
    private static final Pattern TESTE =
            Pattern.compile("(?:^|[^.\\w])test(?:\\.(?:only|fixme|skip))?\\s*\\(\\s*(['\"`])(.*?)\\1");
    private static final Pattern CODIGO = Pattern.compile("^\\s*(CT-\\d+)\\s*[:\\-–]\\s*(.*)$");

    public record Caso(String id, String code, String title, String file, int line,
                       String suite, List<String> tags) {
    }

    public record Modulo(String key, String name, String version, String path,
                         List<String> tags, List<Caso> tests) {
    }

    public record Estrutura(String path, String type, long size) {
    }

    /**
     * Um alvo declarado no playwright.config.ts — o que o projeto chama de
     * "project" e que a tela chama de versão.
     *
     * @param auxiliar projeto que existe para preparar outro (os `setup-*`,
     *                 `seed`): roda como dependência e não faz sentido como
     *                 botão de disparo
     */
    public record Alvo(String name, String testDir, boolean auxiliar) {
    }

    public record Leitura(String projectRoot, int totalTests, List<Modulo> modules,
                          List<Estrutura> structure, List<Alvo> targets) {
    }

    /**
     * Varre a pasta do projeto.
     *
     * @param raiz a pasta que contém {@code tests/} e o {@code playwright.config.ts}
     */
    public Leitura ler(Path raiz) {
        Path testes = raiz.resolve("tests");
        if (!Files.isDirectory(testes))
            throw new IllegalArgumentException("Não encontrei a pasta \"tests\" em " + raiz
                    + ". Aponte o caminho para a raiz do projeto de automação.");

        Map<String, List<Caso>> porModulo = new LinkedHashMap<>();
        Map<String, Modulo> cabecalho = new LinkedHashMap<>();
        int total = 0;

        for (Path arquivo : specs(testes)) {
            String relativo = raiz.relativize(arquivo).toString().replace('\\', '/');
            String[] partes = relativo.split("/");
            // tests/<versão>/<módulo>/<arquivo> — menos que isso é spec solta,
            // sem módulo próprio, e não vira linha na tela.
            if (partes.length < 4) continue;
            String versao = partes[1];
            String modulo = partes[2];
            if (IGNORADAS.contains(versao) || IGNORADAS.contains(modulo)) continue;

            List<Caso> casos = lerSpec(relativo, conteudo(arquivo));
            if (casos.isEmpty()) continue;
            total += casos.size();

            String chave = versao + "/" + modulo;
            porModulo.computeIfAbsent(chave, k -> new ArrayList<>()).addAll(casos);
            cabecalho.computeIfAbsent(chave, k -> new Modulo(chave, humanizar(modulo), versao,
                    "tests/" + versao + "/" + modulo, List.of(), List.of()));
        }

        List<Modulo> modulos = new ArrayList<>();
        porModulo.forEach((chave, casos) -> {
            var base = cabecalho.get(chave);
            var tags = casos.stream().flatMap(c -> c.tags().stream()).distinct().sorted().toList();
            modulos.add(new Modulo(base.key(), base.name(), base.version(), base.path(), tags, casos));
        });
        modulos.sort(Comparator.comparing(Modulo::version).thenComparing(Modulo::name));

        return new Leitura(raiz.toString(), total, modulos, estrutura(raiz), alvos(raiz));
    }

    private static final Pattern PROJETO = Pattern.compile(
            "name:\\s*['\"`]([^'\"`]+)['\"`]", Pattern.MULTILINE);
    private static final Pattern PASTA_DE_TESTE = Pattern.compile(
            "testDir:\\s*['\"`]([^'\"`]+)['\"`]");
    private static final Pattern DEPENDENCIA = Pattern.compile(
            "dependencies:\\s*\\[([^\\]]*)\\]");

    /**
     * Os alvos declarados no playwright.config.ts.
     *
     * É daqui que saem as versões: no Playwright elas são "projects", e é o que
     * o comando escolhe com --project=v2. Não há pasta a apontar na tela — cada
     * project já declara o testDir dele no próprio arquivo de configuração.
     *
     * Projeto citado como dependência de outro é auxiliar: existe para preparar
     * a execução (o login que grava a sessão, a carga de dados) e roda sozinho
     * quando o principal roda. Vira versão inativa, para aparecer no cadastro
     * sem virar botão de disparo.
     */
    public static List<Alvo> alvos(Path raiz) {
        Path config = raiz.resolve("playwright.config.ts");
        if (!Files.isRegularFile(config)) config = raiz.resolve("playwright.config.js");
        if (!Files.isRegularFile(config)) return List.of();

        String texto = conteudo(config);
        int inicio = texto.indexOf("projects:");
        if (inicio < 0) return List.of();
        String bloco = texto.substring(inicio);

        // Quem é citado em dependencies de alguém é preparação, não alvo.
        Set<String> auxiliares = new LinkedHashSet<>();
        Matcher dependencias = DEPENDENCIA.matcher(bloco);
        while (dependencias.find())
            for (String nome : dependencias.group(1).split(","))
                auxiliares.add(nome.replaceAll("['\"`\\s]", ""));

        List<Alvo> encontrados = new ArrayList<>();
        Matcher projetos = PROJETO.matcher(bloco);
        while (projetos.find()) {
            String nome = projetos.group(1);
            if (nome.isBlank()) continue;
            String trecho = bloco.substring(projetos.end(),
                    Math.min(bloco.length(), projetos.end() + 400));
            Matcher pasta = PASTA_DE_TESTE.matcher(trecho);
            encontrados.add(new Alvo(nome, pasta.find() ? pasta.group(1) : null,
                    auxiliares.contains(nome)));
        }
        return encontrados;
    }

    /** Arquivos de teste, em ordem estável para a leitura não mudar de ordem sozinha. */
    private static List<Path> specs(Path testes) {
        try (Stream<Path> caminhos = Files.walk(testes)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(p -> {
                        String nome = p.getFileName().toString();
                        return nome.endsWith(".spec.ts") || nome.endsWith(".spec.js")
                                || nome.endsWith(".test.ts") || nome.endsWith(".test.js");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Não consegui ler a pasta de testes: " + e.getMessage());
        }
    }

    static String conteudo(Path arquivo) {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static List<Caso> lerSpec(String relativo, String conteudo) {
        List<Caso> casos = new ArrayList<>();
        String[] linhas = conteudo.split("\r?\n");
        String suite = null;
        List<String> tagsDaSuite = List.of();

        for (int i = 0; i < linhas.length; i++) {
            Matcher describe = DESCRIBE.matcher(linhas[i]);
            if (describe.find()) {
                String bruto = describe.group(2);
                String nome = semTags(bruto);
                suite = nome.isBlank() ? null : nome;
                tagsDaSuite = tags(bruto);
                continue;
            }

            Matcher teste = TESTE.matcher(linhas[i]);
            if (!teste.find()) continue;

            String bruto = teste.group(2);
            String semTags = semTags(bruto);
            Matcher codigo = CODIGO.matcher(semTags);
            boolean temCodigo = codigo.matches();

            var tags = new LinkedHashSet<>(tagsDaSuite);
            tags.addAll(tags(bruto));

            casos.add(new Caso(relativo + ":" + (i + 1),
                    temCodigo ? codigo.group(1) : null,
                    temCodigo ? codigo.group(2).trim() : semTags,
                    relativo, i + 1, suite, List.copyOf(tags)));
        }
        return casos;
    }

    private static List<String> tags(String texto) {
        List<String> encontradas = new ArrayList<>();
        Matcher m = TAG.matcher(texto);
        while (m.find()) encontradas.add(m.group().substring(1).toLowerCase(Locale.ROOT));
        return encontradas;
    }

    private static String semTags(String texto) {
        return TAG.matcher(texto).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /** `directSale` vira `Direct Sale`. */
    public static String humanizar(String pasta) {
        String espacado = pasta.replaceAll("[-_]+", " ")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
        return espacado.isEmpty() ? pasta
                : Character.toUpperCase(espacado.charAt(0)) + espacado.substring(1);
    }

    /**
     * Um nível da árvore do projeto, para a aba de estrutura.
     *
     * Só o primeiro nível: a árvore inteira de um projeto com node_modules tem
     * centenas de milhares de arquivos e não cabe em tela nenhuma.
     */
    private static List<Estrutura> estrutura(Path raiz) {
        try (Stream<Path> filhos = Files.list(raiz)) {
            return filhos.filter(p -> !p.getFileName().toString().equals("node_modules"))
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .map(p -> new Estrutura(p.getFileName().toString(),
                            Files.isDirectory(p) ? "dir" : "file", tamanho(p)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long tamanho(Path p) {
        try {
            return Files.isDirectory(p) ? 0 : Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }
}
