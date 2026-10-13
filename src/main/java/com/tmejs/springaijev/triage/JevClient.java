package com.tmejs.springaijev.triage;

public interface JevClient {
    JevAssessment assess(String title, String description);
}
