package com.tester.runmanager.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tester.runmanager.entity.RunFailure;

public interface RunFailureRepository extends JpaRepository<RunFailure, Long> {

    List<RunFailure> findByExecutionIdOrderByIdAsc(Long executionId);

    @Query("select f from RunFailure f join f.execution e where e.startedAt between ?1 and ?2 order by e.startedAt desc, f.id asc")
    List<RunFailure> doPeriodo(LocalDateTime de, LocalDateTime ate);

    List<RunFailure> findTop200ByOrderByIdDesc();
}
