package com.tester.runmanager;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tester.runmanager.entity.*;

/** O que a tela de disparo e o histórico trocam com o servidor. */
public final class RunPanelDTOs {

    private RunPanelDTOs() {
    }

    public record CardDTO(Long id, Long containerId, String title, Long environmentId,
                          String environmentName, boolean production, Long systemId,
                          String systemName, Long versionId, String versionName, Long tagId,
                          String tagName, String script, boolean active, Integer position) {

        public static CardDTO de(RunCard c) {
            return new CardDTO(c.getId(),
                    c.getContainer() == null ? null : c.getContainer().getId(),
                    c.getTitle(),
                    c.getEnvironment() == null ? null : c.getEnvironment().getId(),
                    c.getEnvironment() == null ? null : c.getEnvironment().getName(),
                    c.getEnvironment() != null && c.getEnvironment().isProduction(),
                    c.getSystem() == null ? null : c.getSystem().getId(),
                    c.getSystem() == null ? null : c.getSystem().getName(),
                    c.getVersion() == null ? null : c.getVersion().getId(),
                    c.getVersion() == null ? null : c.getVersion().getName(),
                    c.getTag() == null ? null : c.getTag().getId(),
                    c.getTag() == null ? null : c.getTag().getName(),
                    c.getScript(), c.isActive(), c.getPosition());
        }
    }

    public record ContainerDTO(Long id, String title, String description, boolean active,
                               Integer position, List<CardDTO> cards) {

        public static ContainerDTO de(RunContainer c) {
            return new ContainerDTO(c.getId(), c.getTitle(), c.getDescription(), c.isActive(),
                    c.getPosition(), c.getCards().stream().map(CardDTO::de).toList());
        }
    }

    public record ExecutionDTO(Long id, Long cardId, String title, String script,
                               String environmentKey, String systemKey, String engine,
                               String state, Integer exitCode, String triggeredBy,
                               @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss") LocalDateTime startedAt,
                               @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss") LocalDateTime finishedAt,
                               String buildUrl, Long durationSeconds) {

        public static ExecutionDTO de(RunExecution e) {
            Long duracao = e.getStartedAt() == null || e.getFinishedAt() == null ? null
                    : java.time.Duration.between(e.getStartedAt(), e.getFinishedAt()).toSeconds();
            return new ExecutionDTO(e.getId(), e.getCard() == null ? null : e.getCard().getId(),
                    e.getTitle(), e.getScript(), e.getEnvironmentKey(), e.getSystemKey(),
                    e.getEngine(), e.getState(), e.getExitCode(), e.getTriggeredBy(),
                    e.getStartedAt(), e.getFinishedAt(), e.getBuildUrl(), duracao);
        }
    }

    /**
     * Pedaço do log a partir de onde a tela parou de ler.
     *
     * O terminal busca de tempos em tempos mandando o tamanho que já tem; assim
     * a resposta carrega só o que apareceu desde então, em vez do log inteiro a
     * cada segundo.
     */
    public record LogDTO(Long executionId, String state, int offset, String chunk, boolean running) {
    }

    /** Pedido de disparo avulso, sem card — usado pela execução customizada. */
    public record DisparoDTO(String title, String script, Long systemId, Long environmentId) {
    }
}
