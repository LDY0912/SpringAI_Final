package com.skala.helpdesk.audit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

import org.springframework.stereotype.Component;

/** 실습에서 감사 결과를 눈으로 확인하기 위한 최근 500건 인메모리 보관소. */
@Component
public class AuditEventStore {

    public record ToolEvent(
            Instant at,
            String traceId,
            String userId,
            String tool,
            String arguments,
            String result,
            long elapsedMs) {}

    private static final int LIMIT = 500;
    private final ArrayDeque<ToolEvent> events = new ArrayDeque<>();

    public synchronized void add(ToolEvent event) {
        if (events.size() == LIMIT) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<ToolEvent> recent() {
        return events.reversed().stream().toList();
    }

    public synchronized boolean hasToolCall(String traceId) {
        return events.stream().anyMatch(event -> event.traceId().equals(traceId));
    }
}
