package com.tmejs.springaijev.triage;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues/{id}/triage")
public class TriageController {
    private final TriageService triage;

    public TriageController(TriageService triage) { this.triage = triage; }

    @PostMapping
    public TriageResponse run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return triage.run(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    public List<TriageResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return triage.history(UUID.fromString(jwt.getSubject()),
            "MAINTAINER".equals(jwt.getClaimAsString("role")), id);
    }
}
