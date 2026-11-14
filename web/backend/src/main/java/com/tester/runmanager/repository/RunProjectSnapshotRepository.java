package com.tester.runmanager.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunProjectSnapshot;

public interface RunProjectSnapshotRepository extends JpaRepository<RunProjectSnapshot, Long> {
    Optional<RunProjectSnapshot> findFirstBySystemIdOrderByScannedAtDesc(Long systemId);
}
