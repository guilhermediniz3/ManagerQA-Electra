package com.tester.runmanager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.runmanager.entity.*;
import com.tester.runmanager.repository.*;

/**
 * Os casos de teste de um sistema: os lidos do projeto e os cadastrados à mão.
 *
 * A leitura do projeto é guardada; a tela mostra o que foi lido da última vez e
 * quem leu, e reler é uma ação explícita. Isso é de propósito: ler o disco a
 * cada abertura deixaria a tela lenta e, na versão web, ela simplesmente não
 * teria disco para ler.
 */
@Service
public class ProjectCatalogService {

    private final RunSystemRepository sistemas;
    private final RunVersionRepository versoes;
    private final RunProjectSnapshotRepository leituras;
    private final RunManualCaseRepository manuais;
    private final ProjectScanner scanner;
    private final ObjectMapper mapper;

    public ProjectCatalogService(RunSystemRepository sistemas, RunVersionRepository versoes,
                                 RunProjectSnapshotRepository leituras,
                                 RunManualCaseRepository manuais, ProjectScanner scanner,
                                 ObjectMapper mapper) {
        this.sistemas = sistemas;
        this.versoes = versoes;
        this.leituras = leituras;
        this.manuais = manuais;
        this.scanner = scanner;
        this.mapper = mapper;
    }

    public record CasoDTO(String id, String code, String title, String file, Integer line,
                          String suite, List<String> tags, boolean manual, String script) {
    }

    public record ModuloDTO(String key, String name, String version, String path,
                            List<String> tags, List<CasoDTO> tests) {
    }

    public record LeituraDTO(Long systemId, String systemName, String projectRoot,
                             String scannedAt, String scannedBy, int totalModules, int totalTests,
                             List<ModuloDTO> modules, List<ProjectScanner.Estrutura> structure) {
    }

    // ------------------------------------------------------------------ leitura

    @Transactional
    public LeituraDTO reler(Long sistemaId, String quem) {
        RunSystem sistema = exigirSistema(sistemaId);
        if (sistema.getLocalPath() == null || sistema.getLocalPath().isBlank())
            throw new IllegalArgumentException(
                    "Este sistema não tem caminho local configurado. Informe onde o projeto de "
                    + "automação está nesta máquina para que ele possa ser lido.");

        var leitura = scanner.ler(Path.of(sistema.getLocalPath()));

        RunProjectSnapshot registro = new RunProjectSnapshot();
        registro.setSystem(sistema);
        registro.setScannedAt(LocalDateTime.now());
        registro.setScannedBy(quem);
        registro.setProjectRoot(leitura.projectRoot());
        registro.setTotalModules(leitura.modules().size());
        registro.setTotalTests(leitura.totalTests());
        registro.setPayload(escrever(leitura));
        leituras.save(registro);
        registrarVersoes(sistema, leitura.targets());

        return montar(sistema, registro);
    }

    /**
     * Cria as versões a partir dos projects encontrados no projeto.
     *
     * No Playwright, versão é "project": é o que o comando escolhe com
     * --project=v2, e cada um já declara a própria pasta no arquivo de
     * configuração. Cadastrá-las à mão seria copiar uma lista que o projeto já
     * mantém — e que muda sem avisar ninguém.
     *
     * O que já existe não é tocado: quem renomeou ou inativou uma versão tinha
     * um motivo, e uma releitura não pode desfazer isso.
     */
    private void registrarVersoes(RunSystem sistema, List<ProjectScanner.Alvo> alvos) {
        if (alvos == null || alvos.isEmpty()) return;
        var existentes = versoes.findBySystemIdOrderByPositionAscNameAsc(sistema.getId()).stream()
                .map(v -> v.getKey() == null ? "" : v.getKey().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        int posicao = existentes.size();
        for (var alvo : alvos) {
            String chave = alvo.name().toLowerCase(java.util.Locale.ROOT);
            if (existentes.contains(chave)) continue;
            RunVersion versao = new RunVersion();
            versao.setSystem(sistema);
            versao.setName(alvo.name());
            versao.setKey(chave);
            // Auxiliar entra inativo: roda como dependência de outro e não
            // deveria aparecer como opção de disparo.
            versao.setActive(!alvo.auxiliar());
            versao.setPosition(posicao++);
            versoes.save(versao);
        }
    }

    @Transactional(readOnly = true)
    public LeituraDTO casos(Long sistemaId) {
        RunSystem sistema = exigirSistema(sistemaId);
        var registro = leituras.findFirstBySystemIdOrderByScannedAtDesc(sistemaId).orElse(null);
        return montar(sistema, registro);
    }

    /**
     * Junta o que foi lido do projeto com o que foi cadastrado à mão.
     *
     * Os manuais entram nos mesmos módulos quando a chave bate, e formam módulo
     * próprio quando não bate — assim um caso cadastrado para um módulo que
     * ainda não existe no projeto não desaparece da tela.
     */
    private LeituraDTO montar(RunSystem sistema, RunProjectSnapshot registro) {
        List<ModuloDTO> modulos = new ArrayList<>();
        ProjectScanner.Leitura lida = registro == null ? null : ler(registro.getPayload());

        if (lida != null) {
            for (var m : lida.modules()) {
                var casos = new ArrayList<>(m.tests().stream()
                        .map(c -> new CasoDTO(c.id(), c.code(), c.title(), c.file(), c.line(),
                                c.suite(), c.tags(), false, null))
                        .toList());
                modulos.add(new ModuloDTO(m.key(), m.name(), m.version(), m.path(), m.tags(), casos));
            }
        }

        for (var manual : manuais.findBySystemIdOrderByModuleNameAscTitleAsc(sistema.getId())) {
            if (!manual.isActive()) continue;
            var caso = new CasoDTO("manual:" + manual.getId(), manual.getCode(), manual.getTitle(),
                    null, null, null, etiquetas(manual.getTags()), true, manual.getScript());
            String chave = manual.getModuleKey() == null ? "manual" : manual.getModuleKey();

            int existente = indiceDe(modulos, chave);
            if (existente >= 0) {
                var m = modulos.get(existente);
                var casos = new ArrayList<>(m.tests());
                casos.add(caso);
                modulos.set(existente, new ModuloDTO(m.key(), m.name(), m.version(), m.path(),
                        m.tags(), casos));
            } else {
                modulos.add(new ModuloDTO(chave,
                        manual.getModuleName() == null ? "Cadastrados à mão" : manual.getModuleName(),
                        manual.getVersion(), null, List.of(), List.of(caso)));
            }
        }

        int total = modulos.stream().mapToInt(m -> m.tests().size()).sum();
        return new LeituraDTO(sistema.getId(), sistema.getName(),
                registro == null ? null : registro.getProjectRoot(),
                registro == null ? null : registro.getScannedAt().toString(),
                registro == null ? null : registro.getScannedBy(),
                modulos.size(), total, modulos,
                lida == null ? List.of() : lida.structure());
    }

    private static int indiceDe(List<ModuloDTO> modulos, String chave) {
        for (int i = 0; i < modulos.size(); i++) if (modulos.get(i).key().equals(chave)) return i;
        return -1;
    }

    private static List<String> etiquetas(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return java.util.Arrays.stream(tags.split(",")).map(String::trim)
                .filter(t -> !t.isEmpty()).map(t -> t.replaceFirst("^@", "")).toList();
    }

    // ------------------------------------------------------------ casos manuais

    public record ManualDTO(Long id, Long systemId, String moduleKey, String moduleName,
                            String version, String code, String title, String script,
                            String tags, boolean active) {
    }

    @Transactional
    public ManualDTO salvarManual(ManualDTO entrada) {
        RunManualCase caso = entrada.id() == null ? new RunManualCase()
                : manuais.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Caso"));
        if (entrada.title() == null || entrada.title().isBlank())
            throw new IllegalArgumentException("Dê um título ao caso.");
        caso.setSystem(exigirSistema(entrada.systemId()));
        caso.setModuleKey(entrada.moduleKey());
        caso.setModuleName(entrada.moduleName());
        caso.setVersion(entrada.version());
        caso.setCode(entrada.code());
        caso.setTitle(entrada.title().trim());
        caso.setScript(entrada.script());
        caso.setTags(entrada.tags());
        caso.setActive(entrada.active());
        RunManualCase salvo = manuais.save(caso);
        return new ManualDTO(salvo.getId(), salvo.getSystem().getId(), salvo.getModuleKey(),
                salvo.getModuleName(), salvo.getVersion(), salvo.getCode(), salvo.getTitle(),
                salvo.getScript(), salvo.getTags(), salvo.isActive());
    }

    @Transactional
    public void excluirManual(Long id) {
        manuais.delete(manuais.findById(id).orElseThrow(() -> naoEncontrado("Caso")));
    }

    // ------------------------------------------------------------------ apoio

    private RunSystem exigirSistema(Long id) {
        if (id == null) throw new IllegalArgumentException("Escolha o sistema.");
        return sistemas.findById(id).orElseThrow(() -> naoEncontrado("Sistema"));
    }

    private String escrever(ProjectScanner.Leitura leitura) {
        try {
            return mapper.writeValueAsString(leitura);
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui guardar a leitura do projeto.", e);
        }
    }

    private ProjectScanner.Leitura ler(String payload) {
        try {
            return mapper.readValue(payload, ProjectScanner.Leitura.class);
        } catch (Exception e) {
            // Leitura gravada por uma versão anterior, em outro formato: some da
            // tela em vez de derrubá-la, e a próxima releitura resolve.
            return null;
        }
    }

    private static IllegalArgumentException naoEncontrado(String o) {
        return new IllegalArgumentException(o + " não encontrado.");
    }
}
