package com.skala.helpdesk.advisor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;

/** order 900: 모델 호출의 토큰과 지연을 기록한다. */
@Component
public class TokenMeterAdvisor implements CallAdvisor, StreamAdvisor {

    private final MeterRegistry registry;

    public TokenMeterAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        registry.timer("ai.latency", "feature", "chat", "phase", "model")
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        recordUsage(usage(response));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        return Flux.defer(() -> {
            long started = System.nanoTime();
            AtomicReference<Usage> lastUsage = new AtomicReference<>();
            return chain.nextStream(request)
                    .doOnNext(response -> {
                        Usage usage = usage(response);
                        if (usage != null) lastUsage.set(usage);
                    })
                    .doFinally(signal -> {
                        registry.timer("ai.latency", "feature", "chat", "phase", "model")
                                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
                        recordUsage(lastUsage.get());
                    });
        });
    }

    private Usage usage(ChatClientResponse response) {
        return response.chatResponse() == null || response.chatResponse().getMetadata() == null
                ? null
                : response.chatResponse().getMetadata().getUsage();
    }

    private void recordUsage(Usage usage) {
        if (usage == null) return;
        registry.counter("ai.tokens", "type", "prompt", "feature", "chat")
                .increment(value(usage.getPromptTokens()));
        registry.counter("ai.tokens", "type", "completion", "feature", "chat")
                .increment(value(usage.getCompletionTokens()));
    }

    private double value(Integer value) { return value == null ? 0 : value; }
    @Override public String getName() { return "tokenMeter"; }
    @Override public int getOrder() { return 900; }
}
