package com.tester.runmanager;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.RunCatalogDTOs.*;
import com.tester.runmanager.entity.*;
import com.tester.runmanager.repository.*;

/**
 * Cadastro do que o RunManager oferece: sistemas, versões, ambientes e tags.
 *
 * Isto existia como constante no painel — quatro marcações, dois ambientes, três
 * versões, tudo escrito no código. Virou cadastro porque ligar um sistema novo
 * não pode depender de alguém publicar uma versão do aplicativo.
 *
 * Nada aqui é excluído de verdade quando já foi usado: a tela inativa. Execução
 * antiga aponta para o ambiente em que rodou, e apagar o ambiente deixaria o
 * histórico sem sentido.
 */
@Service
public class RunCatalogService {

    private final RunSystemRepository systems;
    private final RunVersionRepository versions;
    private final RunEnvironmentRepository environments;
    private final RunTagRepository tags;

    public RunCatalogService(RunSystemRepository systems, RunVersionRepository versions,
                             RunEnvironmentRepository environments, RunTagRepository tags) {
        this.systems = systems;
        this.versions = versions;
        this.environments = environments;
        this.tags = tags;
    }

    /**
     * Chave usada no comando: minúscula, sem espaço e sem acento.
     *
     * Ela entra na linha de execução e em nome de pipeline, então aceitar
     * qualquer coisa aqui viraria erro lá adiante, quando já não se sabe de onde
     * veio.
     */
    static String chave(String valor, String campo) {
        String bruto = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        String limpo = java.text.Normalizer.normalize(bruto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (limpo.isBlank())
            throw new IllegalArgumentException("Informe " + campo + " usando letras ou números.");
        if (limpo.length() > 40)
            throw new IllegalArgumentException("O identificador de " + campo + " deve ter até 40 caracteres.");
        return limpo;
    }

    private static String exigirNome(String nome) {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("Dê um nome.");
        if (nome.trim().length() > 80)
            throw new IllegalArgumentException("O nome deve ter até 80 caracteres.");
        return nome.trim();
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public CatalogoDTO catalogo() {
        return new CatalogoDTO(listarSistemas(),
                environments.findAllByOrderByPositionAscNameAsc().stream().map(EnvironmentDTO::de).toList(),
                tags.findAllByOrderByPositionAscNameAsc().stream().map(TagDTO::de).toList());
    }

    @Transactional(readOnly = true)
    public List<SystemDTO> listarSistemas() {
        return systems.findAllByOrderByPositionAscNameAsc().stream()
                .map(s -> SystemDTO.de(s, versions.findBySystemIdOrderByPositionAscNameAsc(s.getId())
                        .stream().map(VersionDTO::de).toList()))
                .toList();
    }

    // ------------------------------------------------------------------ sistemas

    @Transactional
    public SystemDTO salvarSistema(SystemDTO entrada) {
        RunSystem s = entrada.id() == null ? new RunSystem() : exigirSistema(entrada.id());
        s.setName(exigirNome(entrada.name()));
        String chave = chave(entrada.key() == null || entrada.key().isBlank() ? entrada.name() : entrada.key(), "o sistema");
        if (entrada.id() == null && systems.existsByKeyIgnoreCase(chave))
            throw new IllegalArgumentException("Já existe um sistema com o identificador \"" + chave + "\".");
        s.setKey(chave);
        s.setRepository(vazioViraNulo(entrada.repository()));
        s.setBranch(vazioViraNulo(entrada.branch()));
        s.setLocalPath(vazioViraNulo(entrada.localPath()));
        s.setSelectionScript(vazioViraNulo(entrada.selectionScript()));
        s.setVariables(vazioViraNulo(entrada.variables()));
        s.setVersionFlag(vazioViraNulo(entrada.versionFlag()));
        s.setActive(entrada.active());
        s.setPosition(entrada.position());
        RunSystem salvo = systems.save(s);
        return SystemDTO.de(salvo, versions.findBySystemIdOrderByPositionAscNameAsc(salvo.getId())
                .stream().map(VersionDTO::de).toList());
    }

    @Transactional
    public void excluirSistema(Long id) {
        RunSystem s = exigirSistema(id);
        versions.deleteAll(versions.findBySystemIdOrderByPositionAscNameAsc(id));
        systems.delete(s);
    }

    // ------------------------------------------------------------------ versões

    @Transactional
    public VersionDTO salvarVersao(VersionDTO entrada) {
        RunVersion v = entrada.id() == null ? new RunVersion()
                : versions.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Versão"));
        if (entrada.systemId() == null)
            throw new IllegalArgumentException("Escolha o sistema da versão.");
        v.setSystem(exigirSistema(entrada.systemId()));
        v.setName(exigirNome(entrada.name()));
        v.setKey(chave(entrada.key() == null || entrada.key().isBlank() ? entrada.name() : entrada.key(), "a versão"));
        v.setActive(entrada.active());
        v.setPosition(entrada.position());
        return VersionDTO.de(versions.save(v));
    }

    @Transactional
    public void excluirVersao(Long id) {
        versions.delete(versions.findById(id).orElseThrow(() -> naoEncontrado("Versão")));
    }

    // ------------------------------------------------------------------ ambientes

    @Transactional
    public EnvironmentDTO salvarAmbiente(EnvironmentDTO entrada) {
        RunEnvironment e = entrada.id() == null ? new RunEnvironment()
                : environments.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Ambiente"));
        e.setName(exigirNome(entrada.name()));
        String chave = chave(entrada.key() == null || entrada.key().isBlank() ? entrada.name() : entrada.key(), "o ambiente");
        if (entrada.id() == null && environments.existsByKeyIgnoreCase(chave))
            throw new IllegalArgumentException("Já existe um ambiente com o identificador \"" + chave + "\".");
        e.setKey(chave);
        e.setProduction(entrada.production());
        e.setVariables(vazioViraNulo(entrada.variables()));
        e.setActive(entrada.active());
        e.setPosition(entrada.position());
        return EnvironmentDTO.de(environments.save(e));
    }

    @Transactional
    public void excluirAmbiente(Long id) {
        environments.delete(environments.findById(id).orElseThrow(() -> naoEncontrado("Ambiente")));
    }

    // ------------------------------------------------------------------ tags

    @Transactional
    public TagDTO salvarTag(TagDTO entrada) {
        RunTag t = entrada.id() == null ? new RunTag()
                : tags.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Marcação"));
        t.setName(exigirNome(entrada.name()));
        String chave = chave(entrada.key() == null || entrada.key().isBlank() ? entrada.name() : entrada.key(), "a marcação");
        if (entrada.id() == null && tags.existsByKeyIgnoreCase(chave))
            throw new IllegalArgumentException("Já existe uma marcação com o identificador \"" + chave + "\".");
        t.setKey(chave);
        t.setDescription(vazioViraNulo(entrada.description()));
        t.setActive(entrada.active());
        t.setPosition(entrada.position());
        return TagDTO.de(tags.save(t));
    }

    @Transactional
    public void excluirTag(Long id) {
        tags.delete(tags.findById(id).orElseThrow(() -> naoEncontrado("Marcação")));
    }

    // ------------------------------------------------------------------ apoio

    private RunSystem exigirSistema(Long id) {
        return systems.findById(id).orElseThrow(() -> naoEncontrado("Sistema"));
    }

    private static IllegalArgumentException naoEncontrado(String o) {
        return new IllegalArgumentException(o + " não encontrado.");
    }

    private static String vazioViraNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
