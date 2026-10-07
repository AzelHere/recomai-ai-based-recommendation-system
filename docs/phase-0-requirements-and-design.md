# AI-Based Recommendation System

## Phase 0: Requirements and solution design

This document records the problem understanding and design for the GUVI Galgotias project. The supplied phase list began with Phase 1, so Phase 0 is used here for requirements analysis and solution design. The project topic remains AI-Based Recommendation System.

## Problem

Users often have to search large catalogs of products or content without knowing what is relevant to their needs. A useful system should use a user's profile, preferences, interests, and recorded behavior to rank suitable catalog entries and explain why each result was selected.

## Proposed solution

Build a Java web application with account/profile management, user-selected preferences, a catalog of categorized items, interaction and feedback capture, and an explainable recommendation service. The first recommendation strategy is a configurable scoring model. It combines category preference, interest or skill match, positive and negative interaction signals, and available rating signals. The service returns the highest scoring eligible items with a human-readable reason. Recommendation logic stays behind an interface so another strategy can be introduced later.

## Users and main workflows

- A visitor registers and signs in, then maintains a profile and preference weights.
- A user browses available categories and items, records interactions, and submits feedback.
- A user requests personalized recommendations and can see the reason associated with each result.
- An administrator maintains catalog entries and reviews basic system activity.

## Functional requirements

1. Store user accounts securely and support profile updates.
2. Store user preferences and interests with validated values.
3. Organize items under categories and maintain item metadata.
4. Record positive and negative user interactions and feedback.
5. Rank candidate items from stored signals and return a configurable top-N list with reasons.
6. Provide web routes for the user and administrator workflows.
7. Persist application data in MySQL using JDBC through dedicated data access classes.

## Non-functional and academic requirements

- Use Java 21, Spring Boot, Spring MVC and embedded Tomcat, MySQL 8, Spring JDBC, Maven, HTML, CSS, and JavaScript.
- Keep controllers, services, recommendation strategies, data access objects, and models in separate layers.
- Demonstrate object-oriented design, justified interfaces and polymorphism, exception handling, and typed collections.
- Validate user input, use parameterized SQL, and never store plain-text passwords.
- Keep the code and setup instructions readable for an academic review.

## Architecture

Browser interface -> Spring MVC controller -> application service -> recommendation strategy where relevant -> JDBC DAO -> MySQL.

The controller translates HTTP requests into service calls. Services validate and coordinate workflows. DAOs own parameterized JDBC queries and row mapping. The recommendation strategy receives domain data and produces ranked results with explanations. The MySQL schema is normalized and uses keys, constraints, and indexes to protect relationships and support lookups.

## Initial recommendation design

For each eligible candidate, compute an explainable weighted score from matching category preferences, matching user/item interests, positive interactions, rating similarity, and other persisted signals. Subtract a penalty for negative interactions. Sort by score descending, apply deterministic tie-breaking, and return at most top-N candidates. The response includes the strongest contributing signal as its reason. Exact weights belong to the recommendation phase and should be easy to tune.

## Data concepts

Users own preferences, interests, interactions, feedback, and stored recommendation results. Items belong to categories and may be associated with interests. Foreign keys maintain these relationships. Category preference weights and feedback values are constrained to valid ranges. This design is represented by the Phase 2 schema.

## Delivery boundaries

Phase 0 covers requirements and design. Phase 1 covers the project skeleton. Phase 2 covers database schema and JDBC setup. Phase 3 covers user accounts, profiles, and preferences. Catalog workflows, interactions, recommendation logic, web routes, user interface, testing, and submission materials remain in their later phases.
