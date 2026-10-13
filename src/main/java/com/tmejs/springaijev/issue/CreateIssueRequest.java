package com.tmejs.springaijev.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIssueRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 10000) String description
) {}
