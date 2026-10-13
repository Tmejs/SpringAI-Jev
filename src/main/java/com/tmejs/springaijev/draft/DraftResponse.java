package com.tmejs.springaijev.draft;

import java.time.Instant;
import java.util.UUID;

public record DraftResponse(UUID id, UUID issueId, UUID createdBy, String content,
        Instant createdAt, Instant updatedAt) {
    public static DraftResponse from(ReplyDraft draft) {
        return new DraftResponse(draft.getId(), draft.getIssue().getId(),
            draft.getCreatedBy().getId(), draft.getContent(), draft.getCreatedAt(),
            draft.getUpdatedAt());
    }
}
