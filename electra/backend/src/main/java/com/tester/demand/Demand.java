package com.tester.demand;
import jakarta.persistence.*;import java.time.Instant;import com.tester.entity.*;
@Entity @Table(name="manager_demand",schema="operational") public class Demand {
 @Id public String jiraKey;
 public String summary;public String statusId;public String statusName;public String assigneeAccountId;public String assigneeName;
 @Column(columnDefinition="TEXT") public String description;
 public String projectKey;public String squad;public String sprint;public String jiraUpdated;public Instant syncedAt;
 public boolean testerOverride;
 @ManyToOne public TesterQA tester;
 @ManyToOne public SystemModule module;
 @ManyToOne public Technology technology;
 @ManyToOne public TestPlan testPlan;
 public String callNumber;
 /** Resumo gerado por IA, serializado como JSON {summary,points,questions}. */
 @Column(columnDefinition="TEXT") public String aiSummary;
 public String aiSummaryProvider;
 public Instant aiSummaryAt;
}
