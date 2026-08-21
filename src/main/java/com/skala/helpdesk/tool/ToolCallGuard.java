package com.skala.helpdesk.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/** 반복 유도 공격으로 한 요청이 도구를 끝없이 부르지 못하도록 요청당 5회로 제한한다. */
@Component
public class ToolCallGuard {

    static final int MAX_CALLS_PER_REQUEST = 5;
    private static final int MAX_TRACKED_REQUESTS = 1_000;

    private final Map<String, Integer> calls = new LinkedHashMap<>();

    public synchronized void check(ToolContext context) {
        String traceId = traceId(context);
        int current = calls.merge(traceId, 1, Integer::sum);
        if (calls.size() > MAX_TRACKED_REQUESTS) {
            calls.remove(calls.keySet().iterator().next());
        }
        if (current > MAX_CALLS_PER_REQUEST) {
            throw new IllegalStateException("한 요청의 도구 호출 상한을 초과했습니다.");
        }
    }

    private String traceId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get("traceId");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("ToolContext에 traceId가 없습니다.");
        }
        return value.toString();
    }
}

