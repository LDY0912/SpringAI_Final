package com.skala.helpdesk.audit;

import java.time.Instant;
import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.audit.AuditEventStore.ToolEvent;

import io.micrometer.core.instrument.MeterRegistry;

/** 모든 @Tool 호출의 도구명·인자·사용자·결과를 마스킹해 기록한다. */
@Aspect
@Component
public class ToolAuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    private final AuditEventStore store;
    private final MeterRegistry registry;

    public ToolAuditAspect(AuditEventStore store, MeterRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String tool = joinPoint.getSignature().getName();
        ToolContext context = Arrays.stream(joinPoint.getArgs())
                .filter(ToolContext.class::isInstance)
                .map(ToolContext.class::cast)
                .findFirst()
                .orElse(null);
        String userId = contextValue(context, "userId", "unknown");
        String traceId = contextValue(context, "traceId", "unknown");
        String arguments = mask(Arrays.stream(joinPoint.getArgs())
                .filter(argument -> !(argument instanceof ToolContext))
                .toList().toString());
        long started = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            save(tool, traceId, userId, arguments, "OK", elapsedMs);
            registry.counter("ai.tool.calls", "tool", tool, "result", "ok").increment();
            return result;
        } catch (Throwable error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            save(tool, traceId, userId, arguments, "FAIL:" + error.getMessage(), elapsedMs);
            registry.counter("ai.tool.calls", "tool", tool, "result", "fail").increment();
            throw error;
        }
    }

    private void save(
            String tool, String traceId, String userId, String arguments, String result, long elapsedMs) {
        String maskedResult = mask(result);
        audit.info("traceId={} user={} tool={} args={} result={} elapsedMs={}",
                traceId, userId, tool, arguments, maskedResult, elapsedMs);
        store.add(new ToolEvent(
                Instant.now(), traceId, userId, tool, arguments, maskedResult, elapsedMs));
    }

    private String contextValue(ToolContext context, String key, String fallback) {
        Object value = context == null ? null : context.getContext().get(key);
        return value == null ? fallback : value.toString();
    }

    private String mask(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("\\d{6}-\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***");
    }
}

