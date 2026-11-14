package com.tester.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tester.entity.GherkinFeature;

@Repository
public interface GherkinFeatureRepository extends JpaRepository<GherkinFeature, Long> {

    List<GherkinFeature> findByTestPlanIdOrderByIdDesc(Long testPlanId);
}
