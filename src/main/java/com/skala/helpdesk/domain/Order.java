package com.skala.helpdesk.domain;

import java.time.LocalDate;

public record Order(
        String id,
        String ownerId,
        String item,
        String status,
        LocalDate estimatedArrival) {}

