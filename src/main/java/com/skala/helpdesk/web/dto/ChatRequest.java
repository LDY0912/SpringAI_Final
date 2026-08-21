package com.skala.helpdesk.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 4000) String message,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sessionId) {}

