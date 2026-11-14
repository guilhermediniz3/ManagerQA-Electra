package com.tester.runmanager.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunExecution;

public interface RunExecutionRepository extends JpaRepository<RunExecution, Long> {

    List<RunExecution> findTop100ByOrderByStartedAtDescIdDesc();

    /** Quantas vezes este card já rodou — é o que impede apagá-lo sem querer. */
    long countByCardId(Long cardId);

    List<RunExecution> findByStateOrderByStartedAtDesc(String state);

    /** Base das métricas: o período inteiro, sem paginar. */
    List<RunExecution> findByStartedAtBetweenOrderByStartedAtAsc(LocalDateTime de, LocalDateTime ate);
}
