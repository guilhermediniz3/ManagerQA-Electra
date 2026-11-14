package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunContainer;

public interface RunContainerRepository extends JpaRepository<RunContainer, Long> {
    List<RunContainer> findAllByOrderByPositionAscIdAsc();
    List<RunContainer> findByActiveTrueOrderByPositionAscIdAsc();
}
