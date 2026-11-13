package com.vmataj.expense;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClaimReviewerTest {
    @Mock ClaimRepository claims;
    @Mock UserDetailsManager users;

    @Test
    void rejectsAnUnknownReviewerBeforeSavingAClaim() {
        var env = new MockEnvironment();
        env.setActiveProfiles("private");
        var controller = new ClaimController(claims, users, env);
        var input = new ClaimController.Submission("Train fare", new BigDecimal("32.40"), "missing-reviewer");

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.submit(input, new TestingAuthenticationToken("employee", "")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(claims);
    }
}
