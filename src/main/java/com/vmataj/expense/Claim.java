package com.vmataj.expense;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
class Claim {
    enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String employee;
    @Column(nullable = false)
    String description;
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;
    @Column(nullable = false)
    Instant submittedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Status status;
    String decidedBy;
    Instant decidedAt;

    protected Claim() {}

    Claim(String employee, String description, BigDecimal amount) {
        this.employee = employee;
        this.description = description;
        this.amount = amount;
        this.submittedAt = Instant.now();
        this.status = Status.PENDING;
    }
}
