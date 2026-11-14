package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunEnvironment;

public interface RunEnvironmentRepository extends JpaRepository<RunEnvironment, Long> {
    List<RunEnvironment> findAllByOrderByPositionAscNameAsc();
    boolean existsByKeyIgnoreCase(String key);
}
