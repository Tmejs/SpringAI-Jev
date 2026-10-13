package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.issue.IssueStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriageRulesTest {
    private final TriageRules rules = new TriageRules(0.70, 0.60);

    @Test
    void belowCategoryConfidenceThresholdNeedsReview() {
        assertEquals(IssueStatus.NEEDS_REVIEW, rules.status(assessment(0.69, 0.91)));
        assertEquals(IssueStatus.TRIAGED, rules.status(assessment(0.70, 0.91)));
    }

    @Test
    void belowInformationThresholdNeedsReview() {
        assertEquals(IssueStatus.NEEDS_REVIEW, rules.status(assessment(0.83, 0.59)));
        assertEquals(IssueStatus.TRIAGED, rules.status(assessment(0.83, 0.60)));
    }

    private JevAssessment assessment(double confidence, double sufficient) {
        return new JevAssessment("jev-1.13.0", JevAssessment.Category.BUG, confidence,
            Map.of("bug", 0.9, "feature", 0.05, "question", 0.05), 1.4, 0.7,
            Map.of("0", 0.1, "1", 0.4, "2", 0.5), sufficient);
    }
}
