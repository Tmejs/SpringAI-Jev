package com.tmejs.springaijev.draft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyDraftRepository extends JpaRepository<ReplyDraft, UUID> {
    List<ReplyDraft> findAllByIssueIdOrderByCreatedAtDesc(UUID issueId);
    Optional<ReplyDraft> findByIdAndIssueId(UUID id, UUID issueId);
}
