package com.skala.helpdesk.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.skala.helpdesk.domain.Ticket;

/** 도구는 PENDING 티켓만 만들고, APPROVED 변경은 관리자 API만 수행한다. */
@Repository
public class TicketRepository {

    private final List<Ticket> tickets = new ArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger(1);

    public synchronized Ticket create(
            String orderId, String userId, Ticket.Type type, String reason) {
        Ticket ticket = new Ticket(
                "RF-%04d".formatted(sequence.getAndIncrement()),
                orderId,
                userId,
                type,
                reason,
                Ticket.Status.PENDING,
                Instant.now());
        tickets.add(ticket);
        return ticket;
    }

    public synchronized List<Ticket> pending() {
        return tickets.stream()
                .filter(ticket -> ticket.status() == Ticket.Status.PENDING)
                .sorted(Comparator.comparing(Ticket::requestedAt))
                .toList();
    }

    public synchronized Optional<Ticket> approve(String no) {
        for (int index = 0; index < tickets.size(); index++) {
            Ticket ticket = tickets.get(index);
            if (ticket.no().equals(no) && ticket.status() == Ticket.Status.PENDING) {
                Ticket approved = new Ticket(
                        ticket.no(), ticket.orderId(), ticket.userId(), ticket.type(), ticket.reason(),
                        Ticket.Status.APPROVED, ticket.requestedAt());
                tickets.set(index, approved);
                return Optional.of(approved);
            }
        }
        return Optional.empty();
    }
}
