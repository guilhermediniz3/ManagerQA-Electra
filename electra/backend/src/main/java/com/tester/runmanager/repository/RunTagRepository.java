package com.tester.runmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tester.runmanager.entity.RunTag;

public interface RunTagRepository extends JpaRepository<RunTag, Long> {
    List<RunTag> findAllByOrderByPositionAscNameAsc();
    boolean existsByKeyIgnoreCase(String key);
}
