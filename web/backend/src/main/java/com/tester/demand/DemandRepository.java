package com.tester.demand;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DemandRepository extends JpaRepository<Demand,String> {boolean existsByTestPlanId(Long id); java.util.Optional<Demand> findByTestPlanId(Long id);}
