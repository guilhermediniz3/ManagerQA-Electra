package com.tester.integration.config;
import jakarta.persistence.*;
@Entity @Table(name="manager_integration",schema="operational")
public class IntegrationSetting {
 @Id public String provider;
 public String baseUrl; public String username; public String model;
 @Column(columnDefinition="TEXT") public String encryptedSecret;
 public String secretSuffix; public String status="NOT_CONFIGURED";
 public String squadField; public String sprintField; public String testerField;
 public boolean defaultProvider;
}
