package com.skala.helpdesk.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.audit.AuditEventStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ObservabilityController {

    private final AuditEventStore auditStore;
    private final MeterRegistry registry;

    public ObservabilityController(AuditEventStore auditStore, MeterRegistry registry) {
        this.auditStore = auditStore;
        this.registry = registry;
    }

    @GetMapping("/audit")
    public List<AuditEventStore.ToolEvent> audit() {
        return auditStore.recent();
    }

    @GetMapping("/metrics-summary")
    public Map<String, Object> metrics() {
        return Map.of(
                "promptTokens", counter("ai.tokens", "type", "prompt"),
                "completionTokens", counter("ai.tokens", "type", "completion"),
                "modelCalls", timerCount("ai.latency"),
                "toolCalls", counterTotal("ai.tool.calls"),
                "safetyRejections", counterTotal("ai.safety.rejections"),
                "fallbackActivations", counterTotal("ai.fallback.activations"),
                "actuator", List.of(
                        "/actuator/metrics/ai.tokens",
                        "/actuator/metrics/ai.latency",
                        "/actuator/metrics/ai.tool.calls",
                        "/actuator/metrics/ai.fallback.activations"));
    }

    private double counter(String name, String tagKey, String tagValue) {
        var counter = Search.in(registry).name(name).tag(tagKey, tagValue).counter();
        return counter == null ? 0 : counter.count();
    }

    private double counterTotal(String name) {
        return Search.in(registry).name(name).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    private long timerCount(String name) {
        return Search.in(registry).name(name).timers().stream()
                .mapToLong(io.micrometer.core.instrument.Timer::count).sum();
    }
}
