package com.skala.helpdesk.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

class OrderToolsTest {

    private TicketRepository tickets;
    private OrderTools tools;
    private TicketTools ticketTools;

    @BeforeEach
    void setUp() {
        tickets = new TicketRepository();
        OrderRepository orders = new OrderRepository();
        ToolCallGuard guard = new ToolCallGuard();
        tools = new OrderTools(orders, guard);
        ticketTools = new TicketTools(orders, tickets, guard);
    }

    @Test
    void 본인_주문만_조회한다() {
        var result = tools.getOrder("12345", context("user1"));

        assertThat(result.status()).isEqualTo("배송중");
        assertThat(result.item()).isEqualTo("무선 이어폰");
    }

    @Test
    void 남의_주문은_없는_주문과_같은_응답이다() {
        var otherUsersOrder = tools.getOrder("99999", context("user1"));
        var unknownOrder = tools.getOrder("00000", context("user1"));

        assertThat(otherUsersOrder.message()).isEqualTo("주문을 찾을 수 없습니다.");
        assertThat(unknownOrder.message()).isEqualTo(otherUsersOrder.message());
        assertThat(otherUsersOrder.status()).isNull();
    }

    @Test
    void 신원은_도구_인자가_아니라_ToolContext에서_온다() {
        var result = tools.getOrder("99999", context("user1"));

        assertThat(result.message()).isEqualTo("주문을 찾을 수 없습니다.");
        assertThat(OrderTools.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("getOrder"))
                .allMatch(method -> method.getParameterCount() == 2);
    }

    @Test
    void 환불은_승인되지_않고_PENDING으로만_접수한다() {
        var result = ticketTools.createTicket("12345", "REFUND", "단순 변심", context("user1"));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.type()).isEqualTo("REFUND");
        assertThat(tickets.pending()).singleElement()
                .extracting(Ticket::status)
                .isEqualTo(Ticket.Status.PENDING);
    }

    @Test
    void 남의_주문은_환불_접수도_차단한다() {
        assertThatThrownBy(() -> ticketTools.createTicket(
                "99999", "REFUND", "환불", context("user1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
        assertThat(tickets.pending()).isEmpty();
    }

    @Test
    void 승인은_별도_저장소_경로에서만_상태를_바꾼다() {
        var requested = ticketTools.createTicket(
                "12345", "REFUND", "단순 변심", context("user1"));
        Ticket approved = tickets.approve(requested.no()).orElseThrow();

        assertThat(approved.status()).isEqualTo(Ticket.Status.APPROVED);
        assertThat(tickets.pending()).isEmpty();
    }

    @Test
    void 교환과_환불은_명시적인_유형으로_접수한다() {
        var exchange = ticketTools.createTicket(
                "12346", "EXCHANGE", "색상 교환", context("user1"));

        assertThat(exchange.type()).isEqualTo("EXCHANGE");
        assertThat(tickets.pending()).singleElement()
                .extracting(Ticket::type)
                .isEqualTo(Ticket.Type.EXCHANGE);
    }

    @Test
    void 한_요청에서_도구를_다섯_번_넘게_부르면_중단한다() {
        ToolCallGuard guard = new ToolCallGuard();
        ToolContext context = context("user1");
        for (int count = 0; count < 5; count++) {
            guard.check(context);
        }

        assertThatThrownBy(() -> guard.check(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("호출 상한");
    }

    private ToolContext context(String userId) {
        return new ToolContext(Map.of("userId", userId, "traceId", "test-trace"));
    }
}
