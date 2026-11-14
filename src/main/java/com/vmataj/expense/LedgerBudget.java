package com.vmataj.expense;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "spendline_budget", uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "budget_month", "category"}))
class LedgerBudget {
    @Id UUID id;
    @Column(nullable = false, length = 40) String owner;
    @Column(name = "budget_month", nullable = false, length = 7) String month;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) LedgerEntry.Category category;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal amount;

    protected LedgerBudget() {}

    LedgerBudget(String owner, String month, LedgerEntry.Category category) {
        id = UUID.randomUUID();
        this.owner = owner;
        this.month = month;
        this.category = category;
    }

    record Limit(LedgerEntry.Category category, BigDecimal amount) {}
}
