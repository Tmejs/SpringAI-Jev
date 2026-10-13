package com.tmejs.springaijev.issue;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {
    private final IssueService issues;

    public IssueController(IssueService issues) {
        this.issues = issues;
    }

    @PostMapping
    public ResponseEntity<IssueResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateIssueRequest request) {
        IssueResponse issue = issues.create(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/issues/" + issue.id())).body(issue);
    }

    @GetMapping("/{id}")
    public IssueResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return issues.get(UUID.fromString(jwt.getSubject()), maintainer(jwt), id);
    }

    @GetMapping
    public List<IssueResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) IssueStatus status) {
        return issues.list(UUID.fromString(jwt.getSubject()), maintainer(jwt), status);
    }

    private boolean maintainer(Jwt jwt) {
        return "MAINTAINER".equals(jwt.getClaimAsString("role"));
    }
}
