package com.tmejs.springaijev.draft;

import jakarta.validation.constraints.NotBlank;

public record EditDraftRequest(@NotBlank String content) {}
