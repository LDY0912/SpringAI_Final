package com.skala.helpdesk.web;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.service.HelpDeskChatService;
import com.skala.helpdesk.web.dto.ChatRequest;
import com.skala.helpdesk.web.dto.ChatResponseDto;

import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/api")
public class HelpDeskController {

    private final HelpDeskChatService chatService;

    public HelpDeskController(HelpDeskChatService chatService) {
        this.chatService = chatService;
    }

    /** 사용자 ID는 요청 본문이 아니라 인증 Principal에서만 가져온다. */
    @PostMapping("/chat")
    public ChatResponseDto chat(
            @Valid @RequestBody ChatRequest request,
            Principal principal) {
        return chatService.chat(principal.getName(), request.sessionId(), request.message());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<HelpDeskChatService.StreamPayload>> stream(
            @Valid @RequestBody ChatRequest request,
            Principal principal) {
        return chatService.stream(principal.getName(), request.sessionId(), request.message());
    }

    @GetMapping("/chat/history")
    public List<HelpDeskChatService.HistoryItem> history(
            @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sessionId,
            Principal principal) {
        return chatService.history(principal.getName(), sessionId);
    }

    @DeleteMapping("/chat/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(
            @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sessionId,
            Principal principal) {
        chatService.clearHistory(principal.getName(), sessionId);
    }
}
