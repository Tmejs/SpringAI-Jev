package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.auth.AppUserRepository;
import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueNotFoundException;
import com.tmejs.springaijev.issue.IssueService;
import com.tmejs.springaijev.triage.TriageRepository;
import com.tmejs.springaijev.triage.TriageResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DraftService {
    private final IssueService issues;
    private final TriageRepository triages;
    private final ReplyGenerator generator;
    private final ReplyDraftRepository drafts;
    private final AppUserRepository users;
    private final TransactionTemplate transactions;

    public DraftService(IssueService issues, TriageRepository triages, ReplyGenerator generator,
            ReplyDraftRepository drafts, AppUserRepository users, TransactionTemplate transactions) {
        this.issues = issues;
        this.triages = triages;
        this.generator = generator;
        this.drafts = drafts;
        this.users = users;
        this.transactions = transactions;
    }

    public DraftResponse generate(UUID issueId, UUID maintainerId) {
        Issue issue = issues.findVisible(maintainerId, true, issueId);
        TriageResult latest = triages.findFirstByIssueIdOrderByCreatedAtDesc(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Triage required"));
        String text = generator.generate(issue, latest);
        if (text == null || text.isBlank()) {
            throw new ReplyInvalidException("Blank model response");
        }
        return transactions.execute(status -> DraftResponse.from(drafts.save(new ReplyDraft(
            issues.findVisible(maintainerId, true, issueId),
            users.findById(maintainerId).orElseThrow(IssueNotFoundException::new), text.trim()))));
    }

    @Transactional(readOnly = true)
    public List<DraftResponse> list(UUID issueId, UUID maintainerId) {
        issues.findVisible(maintainerId, true, issueId);
        return drafts.findAllByIssueIdOrderByCreatedAtDesc(issueId).stream()
            .map(DraftResponse::from).toList();
    }

    @Transactional
    public DraftResponse edit(UUID issueId, UUID draftId, UUID maintainerId, String text) {
        issues.findVisible(maintainerId, true, issueId);
        ReplyDraft draft = drafts.findByIdAndIssueId(draftId, issueId)
            .orElseThrow(IssueNotFoundException::new);
        draft.setContent(text);
        return DraftResponse.from(draft);
    }
}
