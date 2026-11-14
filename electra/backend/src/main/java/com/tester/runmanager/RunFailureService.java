package com.tester.runmanager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tester.integration.IntegrationException;
import com.tester.integration.jira.client.JiraClient;
import com.tester.runmanager.entity.RunFailure;
import com.tester.runmanager.repository.RunFailureRepository;
import com.tester.runmanager.repository.RunSystemRepository;

/**
 * O que se faz com uma falha: mandar o vídeo para o Vimeo e abrir a demanda.
 *
 * As duas ações são separadas e explícitas. Subir o vídeo de toda falha de toda
 * execução encheria a conta do Vimeo sem ninguém pedir, e abrir demanda
 * automática encheria o Jira de tarefa que o time ainda ia triar — quem olha a
 * falha decide se ela é bug.
 */
@Service
public class RunFailureService {

    private final RunFailureRepository falhas;
    private final RunSystemRepository sistemas;
    private final VimeoUploader vimeo;
    private final JiraClient jira;
    private final ObjectMapper mapper;

    public RunFailureService(RunFailureRepository falhas, RunSystemRepository sistemas,
                             VimeoUploader vimeo, JiraClient jira, ObjectMapper mapper) {
        this.falhas = falhas;
        this.sistemas = sistemas;
        this.vimeo = vimeo;
        this.jira = jira;
        this.mapper = mapper;
    }

    /** Dados que a tela preenche antes de abrir a demanda. */
    public record DemandaDTO(String projectKey, String issueType, String summary,
                             String description, boolean enviarVideo) {
    }

    @Transactional
    public String enviarVideo(Long falhaId) {
        RunFailure falha = exigir(falhaId);
        if (falha.getVimeoUrl() != null && !falha.getVimeoUrl().isBlank()) return falha.getVimeoUrl();
        if (falha.getVideoPath() == null || falha.getVideoPath().isBlank())
            throw new IllegalArgumentException("Esta falha não gravou vídeo.");

        String link = vimeo.enviar(caminho(falha, falha.getVideoPath()),
                falha.getTitle() == null ? "Evidência de falha" : falha.getTitle());
        falha.setVimeoUrl(link);
        falhas.save(falha);
        return link;
    }

    @Transactional
    public String criarDemanda(Long falhaId, DemandaDTO pedido) {
        RunFailure falha = exigir(falhaId);
        if (falha.getJiraKey() != null && !falha.getJiraKey().isBlank())
            throw new IllegalArgumentException("Esta falha já virou a demanda " + falha.getJiraKey() + ".");
        if (pedido.projectKey() == null || pedido.projectKey().isBlank())
            throw new IllegalArgumentException("Informe o projeto do Jira.");

        String video = falha.getVimeoUrl();
        if (pedido.enviarVideo() && (video == null || video.isBlank())
                && falha.getVideoPath() != null && !falha.getVideoPath().isBlank()) {
            video = enviarVideo(falhaId);
        }

        var campos = new LinkedHashMap<String, Object>();
        campos.put("project", Map.of("key", pedido.projectKey().trim()));
        campos.put("issuetype", Map.of("name",
                pedido.issueType() == null || pedido.issueType().isBlank() ? "Bug" : pedido.issueType().trim()));
        campos.put("summary", pedido.summary() == null || pedido.summary().isBlank()
                ? resumoPadrao(falha) : pedido.summary().trim());
        campos.put("description", descricao(falha, pedido.description(), video));

        String key = jira.create(mapper.valueToTree(campos));
        falha.setJiraKey(key);
        falhas.save(falha);
        return key;
    }

    private static String resumoPadrao(RunFailure f) {
        String modulo = f.getModuleKey() == null ? "" : "[" + f.getModuleKey() + "] ";
        return (modulo + (f.getTitle() == null ? "Falha na automação" : f.getTitle()));
    }

    /**
     * Descrição em Atlassian Document Format.
     *
     * O log vai em bloco de código, e não em parágrafo: mensagem de erro do
     * Playwright vem com quebras e indentação que carregam significado, e o
     * parágrafo comum as engole. O vídeo entra como link próprio, que o Jira
     * transforma em cartão ao abrir.
     */
    private com.fasterxml.jackson.databind.JsonNode descricao(RunFailure falha, String texto,
                                                              String video) {
        List<Object> conteudo = new ArrayList<>();
        if (texto != null && !texto.isBlank()) conteudo.add(paragrafo(texto.trim()));

        conteudo.add(paragrafo("Caso: " + (falha.getFullName() == null ? falha.getTitle() : falha.getFullName())));
        if (falha.getFile() != null) conteudo.add(paragrafo("Arquivo: " + falha.getFile()));
        if (falha.getBucket() != null) conteudo.add(paragrafo("Classificação: " + falha.getBucket()
                + (falha.getStatusCode() == null ? "" : " (HTTP " + falha.getStatusCode() + ")")));
        if (video != null && !video.isBlank()) conteudo.add(paragrafo("Vídeo: " + video));

        String detalhes = falha.getDetails() == null ? "" : falha.getDetails();
        if (!detalhes.isBlank()) {
            conteudo.add(Map.of("type", "codeBlock", "content",
                    List.of(Map.of("type", "text", "text", cortar(detalhes)))));
        }

        return mapper.valueToTree(Map.of("type", "doc", "version", 1, "content", conteudo));
    }

    /** Parágrafo com texto: um parágrafo de conteúdo vazio o Jira recusa. */
    private static Map<String, Object> paragrafo(String texto) {
        return Map.of("type", "paragraph", "content",
                List.of(Map.of("type", "text", "text", texto)));
    }

    /** Stack de Playwright passa de dezenas de milhares de caracteres. */
    private static String cortar(String texto) {
        return texto.length() <= 20000 ? texto : texto.substring(0, 20000) + "\n[...]";
    }

    /**
     * O relatório do Playwright guarda caminho relativo à raiz do projeto.
     * Resolver a partir do sistema é o que faz o arquivo ser encontrado.
     */
    private Path caminho(RunFailure falha, String arquivo) {
        Path informado = Path.of(arquivo);
        if (informado.isAbsolute()) return informado;
        String raiz = sistemas.findAll().stream()
                .filter(s -> s.getKey() != null
                        && s.getKey().equals(falha.getExecution().getSystemKey()))
                .map(s -> s.getLocalPath()).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (raiz == null)
            throw new IntegrationException("Não sei onde fica o projeto desta execução para achar o vídeo.");
        return Path.of(raiz).resolve(informado);
    }

    private RunFailure exigir(Long id) {
        return falhas.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Falha não encontrada."));
    }
}
