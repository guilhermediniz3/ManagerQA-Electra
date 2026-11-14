package com.tester.runmanager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.entity.RunExecution;
import com.tester.runmanager.entity.RunFailure;
import com.tester.runmanager.repository.RunExecutionRepository;
import com.tester.runmanager.repository.RunFailureRepository;

/**
 * Métricas e falhas do período.
 *
 * As duas coisas saem da mesma base — as execuções e o que elas registraram —,
 * e por isso vivem juntas: o número que aparece no topo da tela de métricas e a
 * lista da aba de falhas precisam concordar entre si, e concordam por serem
 * contados no mesmo lugar.
 *
 * Não há recorte por usuário. O documento pediu assim: a visão é da sessão e do
 * time, não de quem clicou.
 */
@Service
public class RunInsightsService {

    private final RunExecutionRepository execucoes;
    private final RunFailureRepository falhas;

    public RunInsightsService(RunExecutionRepository execucoes, RunFailureRepository falhas) {
        this.execucoes = execucoes;
        this.falhas = falhas;
    }

    public record Resumo(int total, int sucesso, int falha, int emAndamento,
                         double taxaDeSucesso, Long duracaoMediaSegundos) {
    }

    public record PorModulo(String module, String version, int falhas) {
    }

    public record PorBucket(String bucket, int total) {
    }

    public record PorDia(String dia, int total, int sucesso, int falha) {
    }

    public record Metricas(String de, String ate, Resumo resumo, List<PorModulo> modules,
                           List<PorBucket> buckets, List<PorDia> days,
                           List<RunPanelDTOs.ExecutionDTO> ultimas) {
    }

    @Transactional(readOnly = true)
    public Metricas metricas(LocalDate de, LocalDate ate) {
        LocalDate fim = ate == null ? LocalDate.now() : ate;
        LocalDate inicio = de == null ? fim.minusDays(29) : de;
        var periodo = execucoes.findByStartedAtBetweenOrderByStartedAtAsc(
                inicio.atStartOfDay(), LocalDateTime.of(fim, LocalTime.MAX));

        int sucesso = 0, falha = 0, andamento = 0;
        long somaDuracao = 0, concluidas = 0;
        Map<String, int[]> porDia = new LinkedHashMap<>();

        for (RunExecution e : periodo) {
            String dia = e.getStartedAt().toLocalDate().toString();
            int[] contagem = porDia.computeIfAbsent(dia, k -> new int[3]);
            contagem[0]++;
            switch (String.valueOf(e.getState())) {
                case "SUCCESS" -> { sucesso++; contagem[1]++; }
                case "FAILED" -> { falha++; contagem[2]++; }
                case "RUNNING" -> andamento++;
                default -> { }
            }
            if (e.getFinishedAt() != null) {
                somaDuracao += java.time.Duration.between(e.getStartedAt(), e.getFinishedAt()).toSeconds();
                concluidas++;
            }
        }

        int encerradas = sucesso + falha;
        var resumo = new Resumo(periodo.size(), sucesso, falha, andamento,
                encerradas == 0 ? 0 : Math.round(sucesso * 1000.0 / encerradas) / 10.0,
                concluidas == 0 ? null : somaDuracao / concluidas);

        var doPeriodo = falhas.doPeriodo(inicio.atStartOfDay(), LocalDateTime.of(fim, LocalTime.MAX));

        Map<String, Integer> porModulo = new LinkedHashMap<>();
        Map<String, Integer> porBucket = new LinkedHashMap<>();
        for (RunFailure f : doPeriodo) {
            String chave = (f.getVersion() == null ? "?" : f.getVersion()) + "/"
                    + (f.getModuleKey() == null ? "?" : f.getModuleKey());
            porModulo.merge(chave, 1, Integer::sum);
            porBucket.merge(f.getBucket() == null ? "OUTRO" : f.getBucket(), 1, Integer::sum);
        }

        var modulos = porModulo.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entrada -> {
                    String[] partes = entrada.getKey().split("/", 2);
                    return new PorModulo(partes.length > 1 ? partes[1] : partes[0], partes[0],
                            entrada.getValue());
                }).toList();

        var buckets = porBucket.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entrada -> new PorBucket(entrada.getKey(), entrada.getValue())).toList();

        var dias = porDia.entrySet().stream()
                .map(entrada -> new PorDia(entrada.getKey(), entrada.getValue()[0],
                        entrada.getValue()[1], entrada.getValue()[2])).toList();

        var ultimas = periodo.stream()
                .sorted(Comparator.comparing(RunExecution::getStartedAt).reversed())
                .limit(10).map(RunPanelDTOs.ExecutionDTO::de).toList();

        return new Metricas(inicio.toString(), fim.toString(), resumo, modulos, buckets, dias, ultimas);
    }

    // ------------------------------------------------------------------ falhas

    public record FalhaDTO(Long id, Long executionId, String executionTitle, String title,
                           String fullName, String file, String module, String version,
                           String bucket, Integer statusCode, String summary, String details,
                           String videoPath, String screenshotPath, String vimeoUrl, String jiraKey) {

        public static FalhaDTO de(RunFailure f) {
            return new FalhaDTO(f.getId(), f.getExecution().getId(), f.getExecution().getTitle(),
                    f.getTitle(), f.getFullName(), f.getFile(), f.getModuleKey(), f.getVersion(),
                    f.getBucket(), f.getStatusCode(), f.getSummary(), f.getDetails(),
                    f.getVideoPath(), f.getScreenshotPath(), f.getVimeoUrl(), f.getJiraKey());
        }
    }

    @Transactional(readOnly = true)
    public List<FalhaDTO> falhas(Long execucaoId) {
        var lista = execucaoId == null ? falhas.findTop200ByOrderByIdDesc()
                : falhas.findByExecutionIdOrderByIdAsc(execucaoId);
        return lista.stream().map(FalhaDTO::de).toList();
    }
}
