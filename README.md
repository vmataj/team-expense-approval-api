# Spendline Expense Tracker

I built Spendline to see how a simple expense log grows into a useful monthly ledger. Log expenses in EUR, group them by category, set budgets, schedule recurring costs, and export a CSV report. The dashboard opens with an interactive sample ledger. Sign in to keep a private ledger across devices.

[Live app](https://team-expense-approval-api.onrender.com/)

Java 21, Spring Boot, Spring Security, Spring Data JPA, and vanilla JavaScript. Docker Compose runs MySQL locally. Render uses PostgreSQL on Neon. Tesseract.js reads English receipt images in the browser, and Frankfurter supplies exchange rates. Spending insights use category totals, month-over-month comparisons, and budget limits.

## Run it

Run `docker compose up --build` and open [localhost:8082](http://localhost:8082). The original team claims workflow is at `/approvals`.

## Tests

Run `mvn test` for JUnit 5, Mockito, and Spring MockMvc tests covering private ledger access, recurring dates, and approval rules. Add `-Dbrowser.tests=true` for the Selenium WebDriver tests in headless Chrome.
