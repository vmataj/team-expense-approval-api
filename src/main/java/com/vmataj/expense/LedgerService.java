package com.vmataj.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("private")
@Transactional
class LedgerService {
    private final LedgerRepository entries;
    private final LedgerBudgetRepository budgets;
    private final RecurringExpenseRepository recurring;
    private final JdbcTemplate jdbc;

    LedgerService(LedgerRepository entries, LedgerBudgetRepository budgets, RecurringExpenseRepository recurring,
                  JdbcTemplate jdbc) {
        this.entries = entries;
        this.budgets = budgets;
        this.recurring = recurring;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    MonthLedger month(String owner, YearMonth month) {
        return new MonthLedger(rows(owner, month), rows(owner, month.minusMonths(1)),
            budgets.findByOwnerAndMonth(owner, month.toString()).stream()
                .map(budget -> new LedgerBudget.Limit(budget.category, budget.amount)).toList(),
            recurring.findByOwnerOrderByNextDateAscIdAsc(owner).stream().map(RecurringExpense::row).toList());
    }

    private List<LedgerEntry.Row> rows(String owner, YearMonth month) {
        return entries.findByOwnerAndSpentOnBetweenOrderBySpentOnDescIdDesc(owner, month.atDay(1), month.atEndOfMonth())
            .stream().map(LedgerEntry::row).toList();
    }

    void add(String owner, LedgerEntry.Input entry) {
        entries.save(new LedgerEntry(owner, entry, null));
    }

    void update(String owner, UUID id, LedgerEntry.Input input) {
        var entry = entry(owner, id);
        if (entry.ruleId != null && !entry.spentOn.equals(input.spentOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The date of a recurring entry is fixed.");
        }
        entry.update(input);
    }

    void remove(String owner, UUID id) {
        entries.delete(entry(owner, id));
    }

    private LedgerEntry entry(String owner, UUID id) {
        return entries.findByIdAndOwner(id, owner).orElseThrow(() -> missing("Expense"));
    }

    void budget(String owner, YearMonth month, LedgerEntry.Category category, BigDecimal amount) {
        jdbc.queryForObject("select username from users where username = ? for update", String.class, owner);
        var found = budgets.findByOwnerAndMonthAndCategory(owner, month.toString(), category);
        if (amount.signum() == 0) {
            found.ifPresent(budgets::delete);
            return;
        }
        var budget = found.orElseGet(() -> new LedgerBudget(owner, month.toString(), category));
        budget.amount = amount;
        budgets.save(budget);
    }

    void schedule(String owner, RecurringExpense.Input input, LocalDate today) {
        if (input.firstDate().isBefore(today.minusYears(1)) || input.firstDate().isAfter(today.plusYears(5))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a first date within the past year or next five years.");
        }
        postDue(recurring.save(new RecurringExpense(owner, input)), today);
    }

    void stop(String owner, UUID id) {
        recurring.delete(recurring.lock(id, owner).orElseThrow(() -> missing("Recurring expense")));
    }

    void sync(String owner, LocalDate today) {
        recurring.due(owner, today).forEach(rule -> postDue(rule, today));
    }

    private void postDue(RecurringExpense rule, LocalDate today) {
        while (!rule.nextDate.isAfter(today)) {
            entries.save(new LedgerEntry(rule.owner, rule.due(), rule.id));
        }
    }

    private ResponseStatusException missing(String item) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, item + " could not be found.");
    }

    record MonthLedger(List<LedgerEntry.Row> entries, List<LedgerEntry.Row> previous,
                       List<LedgerBudget.Limit> budgets, List<RecurringExpense.Row> recurring) {}
}
