package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.issue.Issue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reply_drafts")
public class ReplyDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ReplyDraft() {}

    public ReplyDraft(Issue issue, AppUser createdBy, String content) {
        this.issue = issue;
        this.createdBy = createdBy;
        this.content = content;
    }

    @PrePersist
    void created() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Issue getIssue() { return issue; }
    public AppUser getCreatedBy() { return createdBy; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setContent(String content) { this.content = content; }
}
