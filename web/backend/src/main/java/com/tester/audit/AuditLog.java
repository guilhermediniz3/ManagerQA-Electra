package com.tester.audit;

import java.time.Instant;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Metadata only: snapshots remain readable after the actor or record is deleted. */
@Entity
@Immutable
@Table(name = "manager_audit_log", schema = "operational", indexes = {
    @Index(name = "manager_audit_log_time", columnList = "occurred_at,id"),
    @Index(name = "manager_audit_log_entity", columnList = "entity_type,entity_id")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "actor_name", nullable = false) private String actorName;
    @Column(name = "actor_email", nullable = false) private String actorEmail;
    @Column(nullable = false, length = 10) private String action;
    @Column(name = "entity_type", nullable = false, length = 100) private String entityType;
    @Column(name = "entity_id", nullable = false) private String entityId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected AuditLog() {}
    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Instant getOccurredAt() { return occurredAt; }
}
