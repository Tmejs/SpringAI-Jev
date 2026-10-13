package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.triage.TriageResult;

public interface ReplyGenerator {
    String generate(Issue issue, TriageResult latest);
}
