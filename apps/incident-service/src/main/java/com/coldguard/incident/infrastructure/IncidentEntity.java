package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident", schema = "incident")
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentEntity() {
        // required by JPA
    }

    public IncidentEntity(UUID id, String assetId, String sensorId, String anomalyType,
            Impact impact, Urgency urgency, Priority priority,
            IncidentStatus status, Instant createdAt) {
        this.id = id;
        this.assetId = assetId;
        this.sensorId = sensorId;
        this.anomalyType = anomalyType;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public Priority getPriority() {
        return priority;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
