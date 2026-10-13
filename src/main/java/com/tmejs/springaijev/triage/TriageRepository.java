package com.tmejs.springaijev.triage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageRepository extends JpaRepository<TriageResult, UUID> {
    List<TriageResult> findAllByIssueIdOrderByCreatedAtDesc(UUID issueId);
    Optional<TriageResult> findFirstByIssueIdOrderByCreatedAtDesc(UUID issueId);
}
