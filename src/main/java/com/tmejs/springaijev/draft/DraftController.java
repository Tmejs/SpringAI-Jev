package com.tmejs.springaijev.draft;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues/{id}/drafts")
public class DraftController {
    private final DraftService drafts;

    public DraftController(DraftService drafts) { this.drafts = drafts; }

    @PostMapping
    public ResponseEntity<DraftResponse> generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        DraftResponse draft = drafts.generate(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/issues/" + id + "/drafts/" + draft.id()))
            .body(draft);
    }

    @GetMapping
    public List<DraftResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return drafts.list(id, UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/{draftId}")
    public DraftResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable UUID draftId, @Valid @RequestBody EditDraftRequest request) {
        return drafts.edit(id, draftId, UUID.fromString(jwt.getSubject()), request.content());
    }
}
