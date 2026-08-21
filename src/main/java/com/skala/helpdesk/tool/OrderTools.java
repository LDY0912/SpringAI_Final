package com.skala.helpdesk.tool;

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.domain.Order;
import com.skala.helpdesk.repository.OrderRepository;

/** 모델에는 설명만 공개하고 사용자 신원은 ToolContext에서만 꺼낸다. */
@Component
public class OrderTools {

    private final OrderRepository orders;
    private final ToolCallGuard callGuard;

    public OrderTools(
            OrderRepository orders,
            ToolCallGuard callGuard) {
        this.orders = orders;
        this.callGuard = callGuard;
    }

    public record OrderView(
            String orderId, String item, String status, String estimatedArrival, String message) {}

    @Tool(description = """
            주문 상태를 조회한다. 사용자가 주문번호를 말하거나 '내 주문', '배송 언제'처럼
            물으면 이 도구를 쓴다. 사용자 본인의 주문만 조회할 수 있다.
            """)
    public OrderView getOrder(
            @ToolParam(description = "조회할 주문번호. 예: 12345") String orderId,
            ToolContext context) {
        callGuard.check(context);
        String userId = currentUser(context);
        return orders.findByIdAndOwnerId(orderId, userId)
                .map(order -> new OrderView(
                        order.id(), order.item(), order.status(),
                        order.estimatedArrival().toString(), "주문 상태를 조회했습니다."))
                .orElseGet(() -> new OrderView(
                        orderId, null, null, null, "주문을 찾을 수 없습니다."));
    }

    @Tool(description = "사용자의 최근 주문을 최대 5건 조회한다. '내 주문'이라고만 물어 주문번호를 모를 때 사용한다.")
    public List<OrderView> getRecentOrders(ToolContext context) {
        callGuard.check(context);
        String userId = currentUser(context);
        return orders.findRecentByOwnerId(userId).stream().map(this::toView).toList();
    }

    private OrderView toView(Order order) {
        return new OrderView(
                order.id(), order.item(), order.status(), order.estimatedArrival().toString(),
                "주문 상태를 조회했습니다.");
    }

    private String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new IllegalStateException("ToolContext에 userId가 없습니다.");
        }
        return userId.toString();
    }

}
