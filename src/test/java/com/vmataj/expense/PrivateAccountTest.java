package com.vmataj.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
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
