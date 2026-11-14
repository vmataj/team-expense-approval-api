package com.vmataj.expense;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "spendline_entry", indexes = @Index(columnList = "owner,spent_on"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"rule_id", "spent_on"}))
class LedgerEntry {
    @Id UUID id;
    @Column(nullable = false, length = 40) String owner;
    @Column(name = "spent_on", nullable = false) LocalDate spentOn;
    @Column(nullable = false, length = 120) String description;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) Category category;
    @Column(name = "rule_id") UUID ruleId;

    protected LedgerEntry() {}

    LedgerEntry(String owner, Input input, UUID ruleId) {
        id = UUID.randomUUID();
        this.owner = owner;
        this.ruleId = ruleId;
        update(input);
    }

    void update(Input input) {
        spentOn = input.spentOn();
        description = input.description().strip();
        amount = input.amount();
        category = input.category();
    }

    Row row() {
        return new Row(id, spentOn, description, amount, category, ruleId != null);
    }

    enum Category { FOOD, TRANSPORT, HOUSING, UTILITIES, HEALTH, SHOPPING, OTHER }

    record Input(@NotNull @PastOrPresent LocalDate spentOn, @NotBlank @Size(max = 120) String description,
                 @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
                 @NotNull Category category) {}

    record Row(UUID id, LocalDate spentOn, String description, BigDecimal amount, Category category, boolean recurring) {}
}
