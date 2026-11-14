package com.vmataj.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("private")
@RequestMapping("/api/ledger")
class LedgerController {
    private final LedgerService ledger;

    LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    ResponseEntity<LedgerService.MonthLedger> month(Principal user, @RequestParam String month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ledger.month(user.getName(), parseMonth(month)));
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sync(Principal user) {
        ledger.sync(user.getName(), LocalDate.now());
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    void add(Principal user, @Valid @RequestBody LedgerEntry.Input input) {
        ledger.add(user.getName(), input);
    }

    @PutMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(Principal user, @PathVariable UUID id, @Valid @RequestBody LedgerEntry.Input input) {
        ledger.update(user.getName(), id, input);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Principal user, @PathVariable UUID id) {
        ledger.remove(user.getName(), id);
    }

    @PutMapping("/budgets/{month}/{category}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void budget(Principal user, @PathVariable String month, @PathVariable LedgerEntry.Category category,
                @Valid @RequestBody Limit input) {
        ledger.budget(user.getName(), parseMonth(month), category, input.amount());
    }

    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    void schedule(Principal user, @Valid @RequestBody RecurringExpense.Input input) {
        ledger.schedule(user.getName(), input, LocalDate.now());
    }

    @DeleteMapping("/recurring/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void stop(Principal user, @PathVariable UUID id) {
        ledger.stop(user.getName(), id);
    }

    private YearMonth parseMonth(String value) {
        if (!value.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid month.");
        }
        return YearMonth.parse(value);
    }

    record Limit(@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
}
