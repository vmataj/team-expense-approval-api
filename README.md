# Team Expense Approval

I built this to practice a two-person approval workflow. An employee submits a claim, a registered reviewer approves or rejects it, and both can see the audit trail. The local demo lets you switch between employee and manager.

[Live app](https://team-expense-approval-api.onrender.com/)

Java 21, Spring Boot, Spring MVC, Spring Security, Spring Data JPA and vanilla JavaScript. Docker Compose runs MySQL locally. The hosted app uses PostgreSQL on Neon.

## Run it

Run `docker compose up --build` and open [localhost:8082](http://localhost:8082).

## Tests

Run `mvn test` for the JUnit 5, Mockito and Spring MockMvc tests. Run `mvn test -Dbrowser.tests=true` to include Selenium WebDriver tests in headless Chrome. Mockito covers reviewer validation, MockMvc covers claim access and decisions, and Selenium submits a claim and filters the ledger.
