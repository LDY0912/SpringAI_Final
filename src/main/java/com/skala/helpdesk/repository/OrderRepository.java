package com.skala.helpdesk.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.skala.helpdesk.domain.Order;

/** 소유자 조건을 쿼리 자체에 넣어 존재 여부까지 노출하지 않는다. */
@Repository
public class OrderRepository {

    private final Map<String, Order> orders = Map.of(
            "12345", new Order("12345", "user1", "무선 이어폰", "배송중",
                    LocalDate.of(2026, 8, 22)),
            "12346", new Order("12346", "user1", "USB-C 케이블", "배송완료",
                    LocalDate.of(2026, 8, 17)),
            "99999", new Order("99999", "user2", "노트북 스탠드", "결제완료",
                    LocalDate.of(2026, 8, 24)));

    public Optional<Order> findByIdAndOwnerId(String orderId, String ownerId) {
        return Optional.ofNullable(orders.get(orderId))
                .filter(order -> order.ownerId().equals(ownerId));
    }

    public List<Order> findRecentByOwnerId(String ownerId) {
        return orders.values().stream()
                .filter(order -> order.ownerId().equals(ownerId))
                .sorted((left, right) -> right.id().compareTo(left.id()))
                .limit(5)
                .toList();
    }
}

