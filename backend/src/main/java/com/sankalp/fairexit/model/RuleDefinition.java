package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the rules table. Read only: rules change through Flyway migrations. */
@Entity
@Table(name = "rules")
public class RuleDefinition {

    @Id
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private int version;

    /** Null means the general set, used by every state without its own rows. */
    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private IndianState state;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parameters;

    @Column(name = "reason_template", nullable = false)
    private String reasonTemplate;

    @Column(nullable = false)
    private boolean enabled;

    protected RuleDefinition() {
        // for JPA
    }

    public String getRuleId() {
        return ruleId;
    }

    public int getVersion() {
        return version;
    }

    public IndianState getState() {
        return state;
    }

    public String getRuleType() {
        return ruleType;
    }

    public int getPriority() {
        return priority;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getReasonTemplate() {
        return reasonTemplate;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
