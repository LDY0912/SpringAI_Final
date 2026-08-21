package com.skala.helpdesk.tool;

import java.util.Locale;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

/** 교환·환불은 접수만 하고, 실제 승인은 관리자 API에 남겨 둔다. */
@Component
public class TicketTools {

    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final ToolCallGuard callGuard;

    public TicketTools(
            OrderRepository orders,
            TicketRepository tickets,
            ToolCallGuard callGuard) {
        this.orders = orders;
        this.tickets = tickets;
        this.callGuard = callGuard;
    }

    public record TicketView(
            String no, String orderId, String type, String status, String message) {}

    @Tool(description = """
            교환·환불 티켓을 접수한다. 처리를 완료하지 않고 PENDING 티켓만 만든다.
            사용자가 명시적으로 교환, 환불, 반품 접수를 요청할 때만 사용한다.
            주문 소유권은 도구 안에서 확인하며 담당자 승인 후 처리된다.
            """)
    public TicketView createTicket(
            @ToolParam(description = "접수할 주문번호. 숫자 문자열 예: 12345") String orderId,
            @ToolParam(description = "접수 유형. EXCHANGE 또는 REFUND") String type,
            @ToolParam(description = "사용자가 말한 사유") String reason,
            ToolContext context) {
        callGuard.check(context);
        String userId = currentUser(context);
        orders.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        Ticket.Type ticketType = parseType(type);
        Ticket ticket = tickets.create(orderId, userId, ticketType, normalizeReason(reason));
        return new TicketView(
                ticket.no(), ticket.orderId(), ticket.type().name(), ticket.status().name(),
                "티켓이 접수되었습니다. 담당자 승인 후 처리됩니다.");
    }

    private Ticket.Type parseType(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("접수 유형은 EXCHANGE 또는 REFUND여야 합니다.");
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        if (normalized.equals("RETURN")) normalized = "REFUND";
        try {
            return Ticket.Type.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("접수 유형은 EXCHANGE 또는 REFUND여야 합니다.");
        }
    }

    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new IllegalStateException("ToolContext에 userId가 없습니다.");
        }
        return userId.toString();
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "사유 미입력" : reason.strip();
    }
}
