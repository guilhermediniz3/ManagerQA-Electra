package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunCard;

public interface RunCardRepository extends JpaRepository<RunCard, Long> {
    List<RunCard> findByContainerIdOrderByPositionAscIdAsc(Long containerId);
}
