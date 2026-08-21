package com.skala.helpdesk.web.dto;

import java.util.List;

public record ChatResponseDto(
        String answer,
        List<String> sources,
        boolean toolUsed,
        String sessionId,
        String traceId) {}
