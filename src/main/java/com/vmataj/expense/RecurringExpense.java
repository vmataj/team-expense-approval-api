package com.vmataj.expense;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "spendline_recurring", indexes = @Index(columnList = "owner,next_date"))
class RecurringExpense {
    @Id UUID id;
    @Column(nullable = false, length = 40) String owner;
    @Column(nullable = false, length = 120) String description;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) LedgerEntry.Category category;
    @Column(nullable = false) int anchorDay;
    @Column(name = "next_date", nullable = false) LocalDate nextDate;

    protected RecurringExpense() {}

    RecurringExpense(String owner, Input input) {
        id = UUID.randomUUID();
        this.owner = owner;
        description = input.description().strip();
        amount = input.amount();
        category = input.category();
        nextDate = input.firstDate();
        anchorDay = nextDate.getDayOfMonth();
    }

    LedgerEntry.Input due() {
        var input = new LedgerEntry.Input(nextDate, description, amount, category);
        YearMonth next = YearMonth.from(nextDate).plusMonths(1);
        nextDate = next.atDay(Math.min(anchorDay, next.lengthOfMonth()));
        return input;
    }

    Row row() {
        return new Row(id, description, amount, category, nextDate, anchorDay);
    }

    record Input(@NotBlank @Size(max = 120) String description, @NotNull @Positive
                 @Digits(integer = 10, fraction = 2) BigDecimal amount, @NotNull LedgerEntry.Category category,
                 @NotNull LocalDate firstDate) {}

    record Row(UUID id, String description, BigDecimal amount, LedgerEntry.Category category,
               LocalDate nextDate, int anchorDay) {}
}
