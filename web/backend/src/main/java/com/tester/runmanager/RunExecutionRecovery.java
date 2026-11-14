package com.tester.runmanager;

import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.repository.RunExecutionRepository;

/**
 * Fecha as execuções que ficaram em aberto quando o aplicativo foi encerrado.
 *
 * O processo do teste vive dentro do aplicativo: fechar a janela no meio de uma
 * execução mata o processo, mas a linha no banco continua dizendo "em
 * execução". Sem isto, ela fica assim para sempre — o histórico passa a mentir,
 * o painel mostra um disparo em andamento que não existe, e as métricas contam
 * esse fantasma todo dia.
 *
 * Acontece ao subir, e não ao encerrar, porque encerramento nem sempre é
 * educado: queda de energia e fim forçado do processo não dão chance de
 * escrever nada.
 */
@Component
public class RunExecutionRecovery {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RunExecutionRecovery.class);

    private final RunExecutionRepository execucoes;

    public RunExecutionRecovery(RunExecutionRepository execucoes) {
        this.execucoes = execucoes;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fecharOQueFicouEmAberto() {
        var orfas = execucoes.findByStateOrderByStartedAtDesc("RUNNING");
        if (orfas.isEmpty()) return;

        for (var execucao : orfas) {
            execucao.setState("CANCELLED");
            execucao.setFinishedAt(LocalDateTime.now());
            String texto = execucao.getLog() == null ? "" : execucao.getLog();
            execucao.setLog(texto + System.lineSeparator()
                    + "-- o aplicativo foi encerrado durante esta execução --");
            execucoes.save(execucao);
        }
        log.info("RunManager: {} execução(ões) que ficaram em aberto foram encerradas.", orfas.size());
    }
}
