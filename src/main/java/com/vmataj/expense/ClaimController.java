package com.vmataj.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/claims")
class ClaimController {
    private final ClaimRepository repository;

    ClaimController(ClaimRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    List<ClaimView> list(Authentication actor) {
        boolean manager = actor.getAuthorities().stream()
            .anyMatch(role -> role.getAuthority().equals("ROLE_MANAGER"));
        List<Claim> claims = manager ? repository.findAllByOrderByIdDesc()
            : repository.findByEmployeeOrderByIdDesc(actor.getName());
        return claims.stream().map(ClaimView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClaimView submit(@Valid @RequestBody Submission input, Authentication actor) {
        return ClaimView.from(repository.save(
            new Claim(actor.getName(), input.description(), input.amount())));
    }

    @PostMapping("/{id}/decision")
    @Transactional
    ClaimView decide(@PathVariable long id, @Valid @RequestBody Decision input, Authentication actor) {
        return close(findForUpdate(id), Claim.Status.valueOf(input.outcome().name()), actor.getName());
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    ClaimView withdraw(@PathVariable long id, Authentication actor) {
        Claim claim = findForUpdate(id);
        if (!claim.employee.equals(actor.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return close(claim, Claim.Status.WITHDRAWN, actor.getName());
    }

    private Claim findForUpdate(long id) {
        return repository.lockById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ClaimView close(Claim claim, Claim.Status status, String actor) {
        if (claim.status != Claim.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim already decided");
        }
        claim.status = status;
        claim.decidedBy = actor;
        claim.decidedAt = Instant.now();
        return ClaimView.from(claim);
    }

    record Submission(@NotBlank @Size(max = 255) String description,
                      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

    enum Outcome { APPROVED, REJECTED }
    record Decision(@NotNull Outcome outcome) {}
    record Event(String action, String actor, Instant time) {}

    record ClaimView(long id, String employee, String description, BigDecimal amount,
                     Claim.Status status, List<Event> history) {
        static ClaimView from(Claim claim) {
            Event submission = new Event("SUBMITTED", claim.employee, claim.submittedAt);
            List<Event> history = claim.status == Claim.Status.PENDING ? List.of(submission)
                : List.of(submission, new Event(claim.status.name(), claim.decidedBy, claim.decidedAt));
            return new ClaimView(claim.id, claim.employee, claim.description, claim.amount, claim.status, history);
        }
    }
}
