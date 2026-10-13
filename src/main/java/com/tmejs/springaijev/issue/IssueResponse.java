package com.tmejs.springaijev.issue;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
    UUID id, UUID ownerId, String title, String description,
    IssueStatus status, Instant createdAt, Instant updatedAt
) {
    public static IssueResponse from(Issue issue) {
        return new IssueResponse(issue.getId(), issue.getOwner().getId(), issue.getTitle(),
            issue.getDescription(), issue.getStatus(), issue.getCreatedAt(), issue.getUpdatedAt());
    }
}
