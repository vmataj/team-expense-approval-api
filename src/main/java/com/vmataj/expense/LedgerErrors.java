package com.vmataj.expense;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = LedgerController.class)
class LedgerErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail problem(ResponseStatusException exception) {
        return exception.getBody();
    }
}
