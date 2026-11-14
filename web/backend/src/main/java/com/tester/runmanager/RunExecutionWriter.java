package com.tester.runmanager;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.entity.RunFailure;
import com.tester.runmanager.repository.RunExecutionRepository;
import com.tester.runmanager.repository.RunFailureRepository;

/**
 * Fecha a execução no banco quando o motor termina.
 *
 * É uma classe à parte, e não um método do serviço, por causa de como a
 * transação funciona: o motor responde de outra thread, minutos depois, e um
 * método chamando a si mesmo dentro da mesma classe não passa pelo proxy do
 * Spring — a anotação seria ignorada e a gravação aconteceria fora de transação.
 */
@Component
public class RunExecutionWriter {

    private final RunExecutionRepository execucoes;
    private final RunFailureRepository falhas;
    private final PlaywrightReportReader relatorio;

    public RunExecutionWriter(RunExecutionRepository execucoes, RunFailureRepository falhas,
                              PlaywrightReportReader relatorio) {
        this.execucoes = execucoes;
        this.falhas = falhas;
        this.relatorio = relatorio;
    }

    /**
     * @param raizDoProjeto onde procurar o relatório do Playwright; nulo quando
     *                      a execução não roda num projeto local
     */
    @Transactional
    public void encerrar(Long id, String estado, Integer codigo, String log, String observacao,
                         String raizDoProjeto) {
        execucoes.findById(id).ifPresent(e -> {
            String texto = log == null ? "" : log;
            if (observacao != null && !observacao.isBlank())
                texto = texto + System.lineSeparator() + observacao;
            e.setLog(texto);
            e.setState(estado);
            e.setExitCode(codigo);
            e.setFinishedAt(LocalDateTime.now());
            execucoes.save(e);
            registrarFalhas(e, raizDoProjeto);
        });
    }

    /**
     * Lê o relatório do Playwright e guarda o que falhou.
     *
     * Acontece no fim da execução, e não quando alguém abre a aba de falhas: o
     * relatório é sobrescrito pela execução seguinte, então quem não lê na hora
     * perde o que aconteceu.
     */
    private void registrarFalhas(com.tester.runmanager.entity.RunExecution execucao, String raiz) {
        if (raiz == null || raiz.isBlank()) return;
        for (var achada : relatorio.ler(java.nio.file.Path.of(raiz))) {
            var classificacao = FailureClassifier.classificar(achada.details());
            RunFailure falha = new RunFailure();
            falha.setExecution(execucao);
            falha.setTitle(achada.title());
            falha.setFullName(achada.fullName());
            falha.setFile(achada.file());
            falha.setModuleKey(achada.module());
            falha.setVersion(achada.version());
            falha.setDetails(achada.details());
            falha.setBucket(classificacao.bucket());
            falha.setSummary(classificacao.summary());
            falha.setStatusCode(classificacao.statusCode());
            falha.setVideoPath(achada.videoPath());
            falha.setScreenshotPath(achada.screenshotPath());
            falhas.save(falha);
        }
    }
}
