# GUVI project submission checklist

## Before the demo

- [ ] Install Java 21 and start MySQL 8.0.16 or newer.
- [ ] Create `ai_recommendation_system` and a least-privilege application account using the SQL in the project README.
- [ ] Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the terminal that will run the app.
- [ ] From the project root, run `.\mvnw.cmd clean verify`.
- [ ] Start the app with `.\mvnw.cmd spring-boot:run` and open `http://localhost:8080/`.
- [ ] Create a normal account and confirm the dashboard loads.
- [ ] Add representative categories and items through the admin catalog editor. Assign an account the `ADMIN` role using the README SQL first.
- [ ] Set user category preferences, then generate recommendations and show the explanation and interaction history.

## Suggested demo sequence

1. Register and sign in.
2. Edit the profile and set category preferences.
3. Browse catalog items and record a positive interaction such as `SAVE`.
4. Generate recommendations and explain the signals available in the current data. The algorithm uses category preference, item/user interest matches, rating signal, and a bounded interaction signal.
5. Record a negative interaction such as `DISLIKE` or `HIDE`, regenerate, and describe the penalty.
6. Sign in as an administrator and show category/item management.

## Rubric evidence

- **Problem understanding and design:** `docs/phase-0-requirements-and-design.md` and the presentation.
- **Core Java:** separate models, services, DAOs, interfaces, an interchangeable recommendation strategy, collections, validation, and exception handling.
- **Database/JDBC:** normalized MySQL DDL in `src/main/resources/schema.sql`; parameterized queries and row mapping in JDBC DAO classes.
- **Web integration:** Spring MVC REST controllers, HTTP session authorization, and the static HTML/CSS/JavaScript dashboard.
- **Automated checks:** the last recorded `clean verify` run passed 25 tests; this does not replace the live demo.

## Scope to describe accurately

- The recommendation strategy is an explainable weighted scoring algorithm, not a trained machine-learning model.
- Feedback and rating tables/signals exist, but submitting a rating through the current application is not implemented.
- The administrator workflow manages catalog entries; it does not provide activity analytics.
- The automated suite does not constitute a full end-to-end test with a live MySQL database and browser.
