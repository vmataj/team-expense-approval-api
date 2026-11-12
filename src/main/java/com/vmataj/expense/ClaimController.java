package com.vmataj.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/claims")
class ClaimController {
    private final ClaimRepository repository;
    private final UserDetailsManager users;
    private final boolean privateMode;

    ClaimController(ClaimRepository repository, UserDetailsManager users, Environment environment) {
        this.repository = repository;
        this.users = users;
        this.privateMode = environment.acceptsProfiles(Profiles.of("private"));
    }

    @GetMapping
    List<ClaimView> list(Authentication actor) {
        if (privateMode) {
            return repository.findVisibleTo(actor.getName())
                .stream().map(ClaimView::from).toList();
        }
        boolean manager = actor.getAuthorities().stream()
            .anyMatch(role -> role.getAuthority().equals("ROLE_MANAGER"));
        List<Claim> claims = manager ? repository.findByReviewerIsNullOrderByIdDesc()
            : repository.findByEmployeeAndReviewerIsNullOrderByIdDesc(actor.getName());
        return claims.stream().map(ClaimView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClaimView submit(@Valid @RequestBody Submission input, Authentication actor) {
        Claim claim = new Claim(actor.getName(), input.description(), input.amount());
        if (privateMode) {
            if (input.reviewer() == null || input.reviewer().equals(actor.getName()) || !users.userExists(input.reviewer())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another registered reviewer");
            }
            claim.reviewer = input.reviewer();
        }
        return ClaimView.from(repository.save(claim));
    }

    @PostMapping("/{id}/decision")
    @Transactional
    ClaimView decide(@PathVariable long id, @Valid @RequestBody Decision input, Authentication actor) {
        Claim claim = findForUpdate(id);
        if (privateMode && !actor.getName().equals(claim.reviewer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return close(claim, Claim.Status.valueOf(input.outcome().name()), actor.getName());
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    ClaimView withdraw(@PathVariable long id, Authentication actor) {
        Claim claim = findForUpdate(id);
        if (!claim.employee.equals(actor.getName()) || (privateMode && claim.reviewer == null)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return close(claim, Claim.Status.WITHDRAWN, actor.getName());
    }

    private Claim findForUpdate(long id) {
        return repository.lockById(id)
            .filter(claim -> privateMode == (claim.reviewer != null))
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
                      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                      @Pattern(regexp = "[a-z0-9][a-z0-9_-]{2,39}") String reviewer) {}

    enum Outcome { APPROVED, REJECTED }
    record Decision(@NotNull Outcome outcome) {}
    record Event(String action, String actor, Instant time) {}

    record ClaimView(long id, String employee, String reviewer, String description, BigDecimal amount,
                     Claim.Status status, List<Event> history) {
        static ClaimView from(Claim claim) {
            Event submission = new Event("SUBMITTED", claim.employee, claim.submittedAt);
            List<Event> history = claim.status == Claim.Status.PENDING ? List.of(submission)
                : List.of(submission, new Event(claim.status.name(), claim.decidedBy, claim.decidedAt));
            return new ClaimView(claim.id, claim.employee, claim.reviewer, claim.description, claim.amount, claim.status, history);
        }
    }
}
