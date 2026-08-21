package com.skala.helpdesk.advisor;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;

/** order 100: 메모리(order 200)에 저장되기 전에 명백한 인젝션과 개인정보 요청을 차단한다. */
@Component
public class SafetyAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String FAILURE_RESPONSE =
            "안전 정책에 따라 해당 요청은 처리할 수 없습니다. 주문이나 반품 관련 질문으로 바꿔 주세요.";

    private static final Logger log = LoggerFactory.getLogger(SafetyAdvisor.class);
    private static final List<Pattern> BLOCK_PATTERNS = List.of(
            Pattern.compile("(?i)(이전|앞의|모든).{0,12}(지시|규칙).{0,8}(무시|삭제)"),
            Pattern.compile("(?i)(시스템|system).{0,8}(프롬프트|prompt).{0,8}(출력|보여|공개)"),
            Pattern.compile("(?i)(규정|안전|권한).{0,8}(무시|우회)"),
            Pattern.compile("(?i)(주민등록번호|카드번호|비밀번호).{0,24}(알려|출력|보여)"),
            Pattern.compile("\\d{6}-\\d{7}"),
            Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}"));

    private final MeterRegistry registry;

    public SafetyAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (blocked(request)) return rejection(request);
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        if (blocked(request)) return Flux.just(rejection(request));
        return chain.nextStream(request);
    }

    private boolean blocked(ChatClientRequest request) {
        String question = request.prompt().getUserMessage().getText();
        return BLOCK_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(question).find());
    }

    private ChatClientResponse rejection(ChatClientRequest request) {
        registry.counter("ai.safety.rejections", "feature", "chat").increment();
        log.warn("안전 차단 traceId={} user={}",
                request.context().getOrDefault(AuditAdvisor.TRACE_ID, "unknown"),
                request.context().getOrDefault("userId", "unknown"));
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(FAILURE_RESPONSE))))
                        .build())
                .context(Map.copyOf(request.context()))
                .build();
    }

    @Override public String getName() { return "safety"; }
    @Override public int getOrder() { return 100; }
}
