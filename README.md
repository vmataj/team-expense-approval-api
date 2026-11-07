# Team Expense Approval API

A Spring Boot application that models a two-role expense review workflow. The browser demo shows the REST API and the resulting audit history.

## Run

Use Java 21 and Maven with `mvn spring-boot:run` (H2), or use `docker compose up --build` (MySQL). Open http://localhost:8080. Submit the sample train fare, then approve or reject it.

## API

Use HTTP Basic with the local fixture accounts `employee` and `manager`, both using `demo-pass`.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /api/claims | Employees see their own claims. Managers see all claims. |
| POST | /api/claims | Employee submits a description and an amount in EUR. |
| POST | /api/claims/{id}/decision | Manager records APPROVED or REJECTED. |

The decision transaction locks the claim row before checking its state. After the first decision commits, a competing request sees the terminal state and gets HTTP 409. The audit history is derived from the persisted submitter, submission time, decision-maker, and decision time.

## Verify

Run `mvn verify`. The integration test submits a claim, checks that employees cannot approve it, races two decisions, and verifies that exactly one decision appears in the audit history. The local demo uses fictional data and fixture accounts.
