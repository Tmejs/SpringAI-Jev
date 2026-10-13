package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.issue.IssueStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TriageResponse(UUID id, UUID issueId, String model, JevAssessment.Category category,
        double categoryConfidence, Map<String, Double> categoryProbabilities, double urgencyScore,
        double urgencyConfidence, Map<String, Double> urgencyProbabilities,
        double sufficientInformationProbability, IssueStatus status, Instant createdAt) {
    public static TriageResponse from(TriageResult result) {
        return new TriageResponse(result.getId(), result.getIssue().getId(), result.getModel(),
            result.getCategory(), result.getCategoryConfidence(), result.getCategoryProbabilities(),
            result.getUrgencyScore(), result.getUrgencyConfidence(), result.getUrgencyProbabilities(),
            result.getSufficientInformationProbability(), result.getOutcome(), result.getCreatedAt());
    }
}
