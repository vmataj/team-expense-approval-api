package com.vmataj.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=${PRIVATE_TEST_JDBC_URL:jdbc:h2:mem:private-accounts;DB_CLOSE_DELAY=-1}")
@AutoConfigureMockMvc
@ActiveProfiles("private")
class PrivateAccountTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LedgerService ledger;

    @Test
    void personalExpensesAndBudgetsAreIsolated() throws Exception {
        MockHttpSession owner = signupAndLogin("ledgerowner");
        MockHttpSession visitor = signupAndLogin("ledgervisitor");
        var ownerToken = token(owner);
        var visitorToken = token(visitor);
        String month = LocalDate.now().toString().substring(0, 7);
        mvc.perform(get("/")).andExpect(forwardedUrl("/ledger.html"));
        mvc.perform(get("/api/ledger").param("month", month)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ledger/entries").session(owner)
                .header(ownerToken.csrfHeader(), ownerToken.csrfToken()).contentType("application/json")
                .content("""
                    {"description":"Metro pass","amount":"22.40","category":"TRANSPORT","spentOn":"%s"}
                    """.formatted(LocalDate.now())))
            .andExpect(status().isCreated());
        mvc.perform(put("/api/ledger/budgets/" + month + "/TRANSPORT").session(owner)
                .header(ownerToken.csrfHeader(), ownerToken.csrfToken()).contentType("application/json")
                .content("{\"amount\":100}"))
            .andExpect(status().isNoContent());
        byte[] body = mvc.perform(get("/api/ledger").param("month", month).session(owner))
            .andExpect(jsonPath("$.entries[0].amount").value(22.40))
            .andExpect(jsonPath("$.budgets[0].amount").value(100))
            .andReturn().getResponse().getContentAsByteArray();
        String id = json.readTree(body).path("entries").get(0).path("id").asText();
        mvc.perform(get("/api/ledger").param("month", month).session(visitor))
            .andExpect(jsonPath("$.entries").isEmpty()).andExpect(jsonPath("$.budgets").isEmpty());
        mvc.perform(delete("/api/ledger/entries/" + id).session(visitor)
                .header(visitorToken.csrfHeader(), visitorToken.csrfToken()))
            .andExpect(status().isNotFound());
    }

    @Test
    void monthlySchedulePostsEachMonthOnceAndReturnsToTheOriginalDay() {
        var january = LocalDate.of(2026, 1, 31);
        var march = LocalDate.of(2026, 3, 31);
        ledger.schedule("schedule-test", new RecurringExpense.Input("Hosting",
            new java.math.BigDecimal("5.00"), LedgerEntry.Category.UTILITIES, january), january);
        ledger.sync("schedule-test", march);
        ledger.sync("schedule-test", march);
        var current = ledger.month("schedule-test", java.time.YearMonth.from(march));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(march),
            current.entries().stream().map(LedgerEntry.Row::spentOn).toList());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(LocalDate.of(2026, 2, 28)),
            current.previous().stream().map(LedgerEntry.Row::spentOn).toList());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 4, 30), current.recurring().getFirst().nextDate());
    }

    @Test
    void onlySubmitterAndAssignedReviewerCanSeeClaims() throws Exception {
        MockHttpSession employee = signupAndLogin("alice");
        MockHttpSession reviewer = signupAndLogin("bob");
        MockHttpSession outsider = signupAndLogin("carol");
        var employeeToken = token(employee);
        var reviewerToken = token(reviewer);
        var outsiderToken = token(outsider);
        mvc.perform(post("/api/claims").session(employee)
                .header(employeeToken.csrfHeader(), employeeToken.csrfToken())
                .contentType("application/json")
                .content("{\"description\":\"Training ticket\",\"amount\":32.40,\"reviewer\":\"alice\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Enter the username of another registered reviewer. You cannot review your own claim."));
        byte[] claim = mvc.perform(post("/api/claims").session(employee)
                .header(employeeToken.csrfHeader(), employeeToken.csrfToken())
                .contentType("application/json").content("""
                    {"description":"Training ticket","amount":32.40,"reviewer":"bob"}
                    """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        long id = json.readTree(claim).path("id").asLong();
        mvc.perform(get("/api/claims").session(outsider))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/claims").session(reviewer))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Training ticket"));
        String decision = "/api/claims/" + id + "/decision";
        mvc.perform(post(decision).session(outsider)
                .header(outsiderToken.csrfHeader(), outsiderToken.csrfToken())
                .contentType("application/json").content("{\"outcome\":\"APPROVED\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post(decision).session(reviewer)
                .header(reviewerToken.csrfHeader(), reviewerToken.csrfToken())
                .contentType("application/json").content("{\"outcome\":\"APPROVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private MockHttpSession signupAndLogin(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var token = token(session);
        mvc.perform(post("/account/signup").session(session)
                .header(token.csrfHeader(), token.csrfToken()).contentType("application/json")
                .content(json.writeValueAsString(new AccountController.Signup(username, "a-local-test-password"))))
            .andExpect(status().isCreated());
        return (MockHttpSession) mvc.perform(post("/login").session(session)
                .param("_csrf", token.csrfToken()).param("username", username).param("password", "a-local-test-password"))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
    }

    private AccountController.Session token(MockHttpSession session) throws Exception {
        byte[] body = mvc.perform(get("/account").session(session))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.readValue(body, AccountController.Session.class);
    }
}
