package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunVersion;

public interface RunVersionRepository extends JpaRepository<RunVersion, Long> {
    List<RunVersion> findBySystemIdOrderByPositionAscNameAsc(Long systemId);
}
