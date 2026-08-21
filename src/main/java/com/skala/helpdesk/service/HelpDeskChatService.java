package com.skala.helpdesk.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.http.codec.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skala.helpdesk.advisor.AuditAdvisor;
import com.skala.helpdesk.audit.AuditEventStore;
import com.skala.helpdesk.config.HelpDeskProperties;
import com.skala.helpdesk.web.dto.ChatResponseDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class HelpDeskChatService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskChatService.class);
    private static final String FALLBACK_MESSAGE =
            "현재 AI 상담이 지연되고 있습니다. 주문·교환·환불 요청은 잠시 후 다시 시도해 주세요.";

    private final ChatClient chat;
    private final ChatMemory memory;
    private final AuditEventStore auditStore;
    private final HelpDeskProperties properties;
    private final MeterRegistry registry;

    public HelpDeskChatService(
            @Qualifier("helpdeskChatClient") ChatClient chat,
            ChatMemory memory,
            AuditEventStore auditStore,
            HelpDeskProperties properties,
            MeterRegistry registry) {
        this.chat = chat;
        this.memory = memory;
        this.auditStore = auditStore;
        this.properties = properties;
        this.registry = registry;
    }

    public ChatResponseDto chat(String userId, String sessionId, String question) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ChatClientResponse clientResponse;
        try {
            clientResponse = chat.prompt()
                    .user(question)
                    .advisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId(userId, sessionId))
                            .param("userId", userId)
                            .param(AuditAdvisor.TRACE_ID, traceId))
                    .toolContext(Map.of("userId", userId, "traceId", traceId))
                    .call()
                    .chatClientResponse();
        } catch (RuntimeException error) {
            fallback(traceId, error);
            return new ChatResponseDto(
                    FALLBACK_MESSAGE, List.of(), false, sessionId, traceId);
        }

        ChatResponse response = clientResponse == null ? null : clientResponse.chatResponse();
        String answer = response == null || response.getResult() == null
                ? "응답을 만들지 못했습니다."
                : response.getResult().getOutput().getText();
        return new ChatResponseDto(
                answer,
                sources(clientResponse == null ? Map.of() : clientResponse.context()),
                auditStore.hasToolCall(traceId),
                sessionId,
                traceId);
    }

    /** 토큰 이벤트를 먼저 보내고, 출처·도구 사용 메타데이터를 마지막 이벤트로 보낸다. */
    public Flux<ServerSentEvent<StreamPayload>> stream(
            String userId, String sessionId, String question) {
        return Flux.defer(() -> {
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            Set<String> usedSources = new LinkedHashSet<>();
            AtomicBoolean emittedToken = new AtomicBoolean();

            Flux<ServerSentEvent<StreamPayload>> tokens = chat.prompt()
                    .user(question)
                    .advisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId(userId, sessionId))
                            .param("userId", userId)
                            .param(AuditAdvisor.TRACE_ID, traceId))
                    .toolContext(Map.of("userId", userId, "traceId", traceId))
                    .stream()
                    .chatClientResponse()
                    .flatMap(response -> {
                        usedSources.addAll(sources(response.context()));
                        String token = text(response.chatResponse());
                        if (token.isBlank()) return Mono.empty();
                        emittedToken.set(true);
                        return Mono.just(ServerSentEvent.<StreamPayload>builder()
                                .event("token")
                                .data(StreamPayload.token(token))
                                .build());
                    });

            Mono<ServerSentEvent<StreamPayload>> completed = Mono.fromSupplier(() ->
                    ServerSentEvent.<StreamPayload>builder()
                            .event("sources")
                            .data(StreamPayload.completed(
                                    List.copyOf(usedSources), sessionId, traceId,
                                    auditStore.hasToolCall(traceId)))
                            .build());

            return tokens.concatWith(completed)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(error -> {
                        if (emittedToken.get()) {
                            log.warn("SSE 종료 예외 traceId={} cause={}",
                                    traceId, error.getClass().getSimpleName());
                            return Flux.just(ServerSentEvent.<StreamPayload>builder()
                                    .event("sources")
                                    .data(StreamPayload.completed(
                                            List.copyOf(usedSources), sessionId, traceId,
                                            auditStore.hasToolCall(traceId)))
                                    .build());
                        }
                        fallback(traceId, error);
                        return Flux.just(
                                ServerSentEvent.<StreamPayload>builder()
                                        .event("token")
                                        .data(StreamPayload.token(FALLBACK_MESSAGE))
                                        .build(),
                                ServerSentEvent.<StreamPayload>builder()
                                        .event("sources")
                                        .data(StreamPayload.completed(
                                                List.of(), sessionId, traceId, false))
                                        .build());
                    });
        });
    }

    public List<HistoryItem> history(String userId, String sessionId) {
        return memory.get(conversationId(userId, sessionId)).stream()
                .map(this::historyItem)
                .toList();
    }

    public void clearHistory(String userId, String sessionId) {
        memory.clear(conversationId(userId, sessionId));
    }

    public record HistoryItem(String role, String text) {}

    private HistoryItem historyItem(Message message) {
        return new HistoryItem(message.getMessageType().name(), message.getText());
    }

    /** 사용자와 세션을 한 곳에서 결합해 다른 사용자의 같은 sessionId와 절대 섞이지 않게 한다. */
    private String conversationId(String userId, String sessionId) {
        return properties.tenantId() + "::" + userId + "::" + sessionId;
    }

    private List<String> sources(Map<String, Object> context) {
        Object value = context.get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(value instanceof List<?> values)) return List.of();
        Set<String> sources = new LinkedHashSet<>();
        values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(document -> String.valueOf(document.getMetadata().getOrDefault("source", "unknown")))
                .forEach(sources::add);
        return List.copyOf(sources);
    }

    private String text(ChatResponse response) {
        return response == null || response.getResult() == null
                ? ""
                : response.getResult().getOutput().getText();
    }

    private void fallback(String traceId, Throwable error) {
        registry.counter("ai.fallback.activations", "feature", "chat").increment();
        log.warn("폴백 응답 traceId={} cause={}", traceId, error.getClass().getSimpleName());
    }

    public record StreamPayload(
            String token,
            List<String> sources,
            String sessionId,
            String traceId,
            Boolean toolUsed,
            String message) {

        static StreamPayload token(String token) {
            return new StreamPayload(token, null, null, null, null, null);
        }

        static StreamPayload completed(
                List<String> sources, String sessionId, String traceId, boolean toolUsed) {
            return new StreamPayload(null, sources, sessionId, traceId, toolUsed, null);
        }

    }
}
