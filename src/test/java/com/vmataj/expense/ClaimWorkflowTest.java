package com.vmataj.expense;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ClaimRepository repository;

    @Test
    void concurrentManagersCanRecordOnlyOneDecision() throws Exception {
        mvc.perform(post("/api/claims").header("Authorization", credentials("employee"))
                .contentType("application/json").content("""
                    {"description":"Train fare","amount":32.40}
                    """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        long id = repository.findByEmployeeOrderByIdDesc("employee").getFirst().id;
        String path = "/api/claims/" + id + "/decision";
        assertThat(decide(path, "employee", "APPROVED")).isEqualTo(403);

        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var approve = pool.submit(() -> {
                start.await();
                return decide(path, "manager", "APPROVED");
            });
            var reject = pool.submit(() -> {
                start.await();
                return decide(path, "manager", "REJECTED");
            });
            start.countDown();
            assertThat(List.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(get("/api/claims").header("Authorization", credentials("employee")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].history.length()").value(2))
            .andExpect(jsonPath("$[0].history[1].actor").value("manager"));
    }

    private int decide(String path, String actor, String outcome) throws Exception {
        return mvc.perform(post(path).header("Authorization", credentials(actor))
            .contentType("application/json").content("{\"outcome\":\"" + outcome + "\"}"))
            .andReturn().getResponse().getStatus();
    }

    @Test
    void onlyTheSubmittingEmployeeCanWithdrawAPendingClaim() throws Exception {
        mvc.perform(post("/api/claims").header("Authorization", credentials("employee"))
                .contentType("application/json").content("""
                    {"description":"Taxi fare","amount":18.50}
                    """))
            .andExpect(status().isCreated());
        long id = repository.findByEmployeeOrderByIdDesc("employee").getFirst().id;
        String path = "/api/claims/" + id + "/withdraw";
        mvc.perform(get("/api/claims").header("Authorization", credentials("employee2")))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post(path).header("Authorization", credentials("employee2")))
            .andExpect(status().isNotFound());
        mvc.perform(post(path).header("Authorization", credentials("manager")))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", credentials("employee")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.history[1].action").value("WITHDRAWN"));
        mvc.perform(post(path).header("Authorization", credentials("employee")))
            .andExpect(status().isConflict());
    }

    private String credentials(String username) {
        return "Basic " + Base64.getEncoder().encodeToString(
            (username + ":demo-pass").getBytes(StandardCharsets.UTF_8));
    }
}
