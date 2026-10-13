package com.tmejs.springaijev.issue;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {
    private final IssueRepository issues;
    private final AppUserRepository users;

    public IssueService(IssueRepository issues, AppUserRepository users) {
        this.issues = issues;
        this.users = users;
    }

    @Transactional
    public IssueResponse create(UUID actorId, CreateIssueRequest request) {
        AppUser owner = users.findById(actorId).orElseThrow(IssueNotFoundException::new);
        return IssueResponse.from(issues.save(new Issue(owner, request.title(), request.description())));
    }

    @Transactional(readOnly = true)
    public IssueResponse get(UUID actorId, boolean maintainer, UUID issueId) {
        return IssueResponse.from(findVisible(actorId, maintainer, issueId));
    }

    @Transactional(readOnly = true)
    public Issue findVisible(UUID actorId, boolean maintainer, UUID issueId) {
        return maintainer
            ? issues.findById(issueId).orElseThrow(IssueNotFoundException::new)
            : issues.findByIdAndOwnerId(issueId, actorId).orElseThrow(IssueNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> list(UUID actorId, boolean maintainer, IssueStatus filter) {
        List<Issue> found = maintainer
            ? (filter == null ? issues.findAllByOrderByCreatedAtDesc()
                : issues.findAllByStatusOrderByCreatedAtDesc(filter))
            : (filter == null ? issues.findAllByOwnerIdOrderByCreatedAtDesc(actorId)
                : issues.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(actorId, filter));
        return found.stream().map(IssueResponse::from).toList();
    }
}
