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

## Registered accounts

For the registered-user workflow, set `APP_PROFILE=private` in the shell before running
`docker compose up --build`. Create two accounts. Submit a claim with the other account as reviewer, then sign in as that reviewer to approve or reject it.
Docker Compose supplies persistent MySQL and allows cookies over local HTTP.
The default profile uses the original local demo workflow.

For HTTPS hosting, activate the `private` Spring profile and supply
`SERVER_ADDRESS=0.0.0.0`, `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
Use a dedicated database for this app and inject the password from the hosting
provider's secret store. The service honors `PORT` and requires HTTPS cookies by default.
The bundled Cloud SQL connector accepts a JDBC URL of the form
`jdbc:mysql:///DATABASE?cloudSqlInstance=PROJECT:REGION:INSTANCE&socketFactory=com.google.cloud.sql.mysql.SocketFactory&cloudSqlRefreshStrategy=lazy`.
For a first Cloud Run deployment, limit the service to one instance because login sessions are held in memory.

## Tests

Run `mvn verify`. The integration test submits a claim, checks that employees cannot approve it, races two decisions, and verifies that exactly one decision appears in the audit history. The local demo uses fictional data and fixture accounts.
