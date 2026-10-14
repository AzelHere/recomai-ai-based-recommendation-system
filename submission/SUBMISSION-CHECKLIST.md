# GUVI project submission checklist

## Before the demo

- [ ] Start Docker Desktop and run `.\start-local.ps1` from the project folder.
- [ ] Confirm both containers are healthy and open `http://localhost:8080/`.
- [ ] Register and sign in, then complete the short onboarding flow: goal, one of five niches, and up to three optional topics.
- [ ] Browse the seeded resource catalog and open at least one source link.
- [ ] Search for a topic/resource and show how it becomes a low-strength interest signal.
- [ ] Use Like, Save, More like this, and Not interested; refresh recommendations and the Interest Profile.
- [ ] Mark a resource In progress and Completed, add one to a named collection, and show the Progress view.
- [ ] Demonstrate the dark/light theme toggle and its remembered setting.
- [ ] Download a personal JSON export and explain the account-safe personalization reset.
- [ ] Sign in as an administrator and demonstrate resource/category management, current analytics, and share settings with preview/audit.
- [ ] Run `.\mvnw.cmd clean verify` and report the observed result.

## Suggested demo sequence

1. Register, sign in, and complete onboarding in a few clicks.
2. Show the Explore feed sections: familiar, related, and discovery picks.
3. Open a seeded public resource and show its source, topics, difficulty, and time estimate.
4. Save or like a resource, then show its topic profile evidence and refreshed recommendation explanation.
5. Mark a resource Not interested; show that it is suppressed and its topic receives negative evidence.
6. Explain that repeated activity updates confidence and recency-weighted topic scores; these are estimates, not facts.
7. Show My Interest Profile and distinguish selected onboarding information from inferred behavior.

## Rubric evidence

- **Problem understanding and design:** the five-niche MVP, onboarding, feed strategy, and explanations are documented in the README and `docs/phase-0-requirements-and-design.md`.
- **Core Java:** separate models, services, DAOs, interfaces, collections, validation, exception handling, and a replaceable recommendation strategy.
- **Database/JDBC:** normalized MySQL tables and relationships are managed through Flyway V1-V10; DAO queries use Spring JDBC parameters.
- **Web integration:** Spring MVC controllers, session-based authorization, and the static HTML/CSS/JavaScript client.
- **Automated checks:** Java 21 tests plus Playwright journeys run via the isolated MySQL 8.4 E2E runner. State the exact observed result of the latest `npm run test:e2e`; do not infer unimplemented analytics metrics from existing admin counts.

## Scope to describe accurately

- The engine is an explainable scoring and preference-learning system, not a trained machine-learning model or an LLM.
- Admin analytics is based on recorded activity and recommendation batches; impression-based CTR/save rates, the signup funnel, user administration, and demo-data controls are not implemented yet.
- Behavior and onboarding influence decay over time; event weights and the 70/20/10 mix can be configured.
- Emerging-interest indicators are estimates based on repeated direction/confidence evidence, not statements of certainty.
- Resource records hold metadata and external links; the project does not scrape or copy third-party articles.
