package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.issue.IssueStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TriageRules {
    private final double categoryConfidenceThreshold;
    private final double sufficientInformationThreshold;

    public TriageRules(@Value("${app.triage.category-confidence-threshold:0.70}") double categoryConfidenceThreshold,
            @Value("${app.triage.sufficient-information-threshold:0.60}") double sufficientInformationThreshold) {
        this.categoryConfidenceThreshold = categoryConfidenceThreshold;
        this.sufficientInformationThreshold = sufficientInformationThreshold;
    }

    public IssueStatus status(JevAssessment assessment) {
        return assessment.categoryConfidence() < categoryConfidenceThreshold
            || assessment.sufficientInformationProbability() < sufficientInformationThreshold
            ? IssueStatus.NEEDS_REVIEW : IssueStatus.TRIAGED;
    }
}
