package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "triage_results")
public class TriageResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Column(nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JevAssessment.Category category;

    @Column(nullable = false)
    private double categoryConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> categoryProbabilities;

    @Column(nullable = false)
    private double urgencyScore;

    @Column(nullable = false)
    private double urgencyConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> urgencyProbabilities;

    @Column(nullable = false)
    private double sufficientInformationProbability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IssueStatus outcome;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TriageResult() {}

    public TriageResult(Issue issue, JevAssessment assessment, IssueStatus outcome) {
        this.issue = issue;
        this.model = assessment.model();
        this.category = assessment.category();
        this.categoryConfidence = assessment.categoryConfidence();
        this.categoryProbabilities = Map.copyOf(assessment.categoryProbabilities());
        this.urgencyScore = assessment.urgencyScore();
        this.urgencyConfidence = assessment.urgencyConfidence();
        this.urgencyProbabilities = Map.copyOf(assessment.urgencyProbabilities());
        this.sufficientInformationProbability = assessment.sufficientInformationProbability();
        this.outcome = outcome;
    }

    @PrePersist
    void created() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public Issue getIssue() { return issue; }
    public String getModel() { return model; }
    public JevAssessment.Category getCategory() { return category; }
    public double getCategoryConfidence() { return categoryConfidence; }
    public Map<String, Double> getCategoryProbabilities() { return categoryProbabilities; }
    public double getUrgencyScore() { return urgencyScore; }
    public double getUrgencyConfidence() { return urgencyConfidence; }
    public Map<String, Double> getUrgencyProbabilities() { return urgencyProbabilities; }
    public double getSufficientInformationProbability() { return sufficientInformationProbability; }
    public IssueStatus getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }

    public String summary() {
        return "Category: " + category + " (confidence " + categoryConfidence + ")"
            + "; urgency: " + urgencyScore + " (confidence " + urgencyConfidence + ")"
            + "; sufficient information probability: " + sufficientInformationProbability
            + "; status: " + outcome;
    }
}
