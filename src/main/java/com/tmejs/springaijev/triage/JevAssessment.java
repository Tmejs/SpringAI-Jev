package com.tmejs.springaijev.triage;

import java.util.Map;

public record JevAssessment(
    String model,
    Category category,
    double categoryConfidence,
    Map<String, Double> categoryProbabilities,
    double urgencyScore,
    double urgencyConfidence,
    Map<String, Double> urgencyProbabilities,
    double sufficientInformationProbability
) {
    public enum Category { BUG, FEATURE, QUESTION }
}
