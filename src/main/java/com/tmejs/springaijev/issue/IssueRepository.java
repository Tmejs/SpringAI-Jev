package com.tmejs.springaijev.issue;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, UUID> {
    Optional<Issue> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<Issue> findAllByOrderByCreatedAtDesc();
    List<Issue> findAllByStatusOrderByCreatedAtDesc(IssueStatus status);
    List<Issue> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<Issue> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, IssueStatus status);
}
