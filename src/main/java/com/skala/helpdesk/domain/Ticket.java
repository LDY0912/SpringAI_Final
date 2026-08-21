package com.skala.helpdesk.domain;

import java.time.Instant;

public record Ticket(
        String no,
        String orderId,
        String userId,
        Type type,
        String reason,
        Status status,
        Instant requestedAt) {

    public enum Type { EXCHANGE, REFUND }
    public enum Status { PENDING, APPROVED }
}
