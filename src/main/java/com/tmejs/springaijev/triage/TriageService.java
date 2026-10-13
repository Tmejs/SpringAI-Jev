package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueService;
import com.tmejs.springaijev.issue.IssueStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TriageService {
    private final IssueService issues;
    private final JevClient jev;
    private final TriageRules rules;
    private final TriageRepository triages;
    private final TransactionTemplate transactions;

    public TriageService(IssueService issues, JevClient jev, TriageRules rules,
            TriageRepository triages, TransactionTemplate transactions) {
        this.issues = issues;
        this.jev = jev;
        this.rules = rules;
        this.triages = triages;
        this.transactions = transactions;
    }

    public TriageResponse run(UUID issueId, UUID actorId) {
        Issue issue = issues.findVisible(actorId, true, issueId);
        JevAssessment assessment = jev.assess(issue.getTitle(), issue.getDescription());
        return transactions.execute(status -> {
            Issue current = issues.findVisible(actorId, true, issueId);
            IssueStatus outcome = rules.status(assessment);
            current.setStatus(outcome);
            return TriageResponse.from(triages.save(new TriageResult(current, assessment, outcome)));
        });
    }

    public List<TriageResponse> history(UUID actorId, boolean maintainer, UUID issueId) {
        issues.findVisible(actorId, maintainer, issueId);
        return triages.findAllByIssueIdOrderByCreatedAtDesc(issueId).stream()
            .map(TriageResponse::from).toList();
    }
}
