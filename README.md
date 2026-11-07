# Team Expense Approval API

A Spring Boot application that models a two-role expense review workflow. The browser demo shows the REST API and the resulting audit history.

## Run

Use Java 21 and Maven with `mvn spring-boot:run` (in-memory H2), or use `docker compose up --build` (persistent MySQL). Open http://localhost:8082. Submit a claim as an employee, then switch to the manager to approve or reject it. Employees can withdraw their own pending claims. Use the status filter to review closed claims and their audit history.

## API

Use HTTP Basic with the local fixture accounts `employee`, `employee2`, and `manager`, all using `demo-pass`.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /api/claims | Employees see their own claims. Managers see all claims. |
| POST | /api/claims | Employee submits a description and an amount in EUR. |
| POST | /api/claims/{id}/decision | Manager records APPROVED or REJECTED. |
| POST | /api/claims/{id}/withdraw | Submitting employee withdraws a pending claim. |

The decision transaction locks the claim row before checking its state. After the first decision commits, a competing request sees the terminal state and gets HTTP 409. The audit history is derived from the persisted submitter, submission time, decision-maker, and decision time.

## Verify

Run `mvn verify`. The integration test submits a claim, checks that employees cannot approve it, races two decisions, and verifies that exactly one decision appears in the audit history. The local demo uses fictional data and fixture accounts.
