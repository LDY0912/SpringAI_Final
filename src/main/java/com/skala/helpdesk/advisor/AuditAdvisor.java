package com.skala.helpdesk.advisor;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

/** order 0: 차단 응답을 포함해 요청 전체를 가장 바깥에서 추적한다. */
@Component
public class AuditAdvisor implements BaseAdvisor {

    public static final String TRACE_ID = "traceId";
    public static final String STARTED_NANOS = "auditStartedNanos";
    private static final Logger audit = LoggerFactory.getLogger("AI_ADVISOR_AUDIT");

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String traceId = String.valueOf(request.context().getOrDefault(TRACE_ID, newTraceId()));
        audit.info("traceId={} phase=request user={} question={}",
                traceId,
                request.context().getOrDefault("userId", "unknown"),
                mask(request.prompt().getUserMessage().getText()));
        return request.mutate()
                .context(TRACE_ID, traceId)
                .context(STARTED_NANOS, System.nanoTime())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        String traceId = String.valueOf(response.context().getOrDefault(TRACE_ID, "unknown"));
        long started = (long) response.context().getOrDefault(STARTED_NANOS, System.nanoTime());
        String answer = response.chatResponse() == null || response.chatResponse().getResult() == null
                ? ""
                : response.chatResponse().getResult().getOutput().getText();
        audit.info("traceId={} phase=response elapsedMs={} answer={}",
                traceId, (System.nanoTime() - started) / 1_000_000, mask(answer));
        return response;
    }

    @Override public String getName() { return "audit"; }
    @Override public int getOrder() { return 0; }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String mask(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("\\d{6}-\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***");
    }
}

