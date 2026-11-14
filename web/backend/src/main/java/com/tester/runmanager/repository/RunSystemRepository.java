package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunSystem;

public interface RunSystemRepository extends JpaRepository<RunSystem, Long> {
    List<RunSystem> findAllByOrderByPositionAscNameAsc();
    List<RunSystem> findByActiveTrueOrderByPositionAscNameAsc();
    boolean existsByKeyIgnoreCase(String key);
}
