package com.tester.integration.config;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IntegrationRepository extends JpaRepository<IntegrationSetting,String> {}
