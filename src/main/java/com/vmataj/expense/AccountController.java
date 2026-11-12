package com.vmataj.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("private")
@RequestMapping("/account")
class AccountController {
    private static final Set<String> RESERVED = Set.of("employee", "employee2", "manager");
    private final UserDetailsManager users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    AccountController(UserDetailsManager users) {
        this.users = users;
    }

    @GetMapping
    ResponseEntity<Session> session(Authentication actor, CsrfToken csrf) {
        String username = actor == null || actor instanceof AnonymousAuthenticationToken ? null : actor.getName();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Session(username, csrf.getHeaderName(), csrf.getToken()));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void signup(@Valid @RequestBody Signup signup) {
        if (RESERVED.contains(signup.username()) ||
                signup.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username or password");
        }
        try {
            users.createUser(User.withUsername(signup.username())
                .password("{bcrypt}" + encoder.encode(signup.password())).roles("USER").build());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        }
    }

    record Session(String username, String csrfHeader, String csrfToken) {}
    record Signup(@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{2,39}") String username,
                  @NotBlank @Size(min = 12, max = 72) String password) {}
}
