package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunManualCase;

public interface RunManualCaseRepository extends JpaRepository<RunManualCase, Long> {
    List<RunManualCase> findBySystemIdOrderByModuleNameAscTitleAsc(Long systemId);
}
