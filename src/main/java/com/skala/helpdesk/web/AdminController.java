package com.skala.helpdesk.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.repository.TicketRepository;

/** 도구 목록에 등록하지 않은 사람 전용 승인 경로다. */
@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final TicketRepository tickets;

    public AdminController(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/pending")
    public List<Ticket> pending() {
        return tickets.pending();
    }

    @PostMapping("/{no}/approve")
    public Ticket approve(@PathVariable String no) {
        return tickets.approve(no)
                .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다."));
    }
}
