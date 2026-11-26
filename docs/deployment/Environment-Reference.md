# Environment reference
Do not commit a real environment file. The existing local `.env` is preserved. Use a separate private production environment file; placeholders in `.env.example` must be replaced before use. This inventory is extracted from actual properties/Compose, not invented settings. Variables forwarded by Compose are explicitly visible in docker-compose.yml; other Spring properties can be supplied directly to the container as described below. Do not print `docker compose config` with real secrets into shared logs.

## APP_HOST_PORT

Purpose: Loopback application published port for host reverse proxy. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## APP_PUBLIC_URL

Purpose: Local trusted browser origin; production profile uses PRODUCTION_PUBLIC_URL. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## COMPOSE_PROFILES

Purpose: local-mail enables Mailpit only for local development. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## DB_PASSWORD

Purpose: Secret credential; unique random value. Required for DB/root or authenticated production SMTP. Never publish.. Development example: `<private-secret>`. Production: Required; secret store or protected private env file.

## DB_POOL_SIZE

Purpose: Production Hikari maximum connection pool, default 10. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## DB_URL

Purpose: JDBC URL when running Spring directly; Compose derives it from MYSQL_DATABASE and service mysql. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## DB_USERNAME

Purpose: Application MySQL account, not root. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## FLYWAY_BASELINE_ON_MIGRATE

Purpose: false by default; never enable to hide an incompatible schema. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## MAILPIT_HOST_PORT

Purpose: Development email capture loopback UI port. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## MYSQL_DATABASE

Purpose: Schema created by MySQL container on a new empty volume. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## MYSQL_HOST_PORT

Purpose: Development-only loopback published database port; production overlay removes it. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## MYSQL_ROOT_PASSWORD

Purpose: Secret credential; unique random value. Required for DB/root or authenticated production SMTP. Never publish.. Development example: `<private-secret>`. Production: Required; secret store or protected private env file.

## PASSWORD_RESET_COOLDOWN_SECONDS

Purpose: Secret credential; unique random value. Required for DB/root or authenticated production SMTP. Never publish.. Development example: `<private-secret>`. Production: Required; secret store or protected private env file.

## PORT

Purpose: Direct Spring listener override; nested default precedes SERVER_PORT. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## PRODUCTION_PUBLIC_URL

Purpose: Exact HTTPS origin without query, fragment or credentials. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## PRODUCTION_SMTP_HOST

Purpose: Authenticated production SMTP server. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## PRODUCTION_SMTP_PORT

Purpose: Production SMTP port, default 587. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## RECOMMENDATION_DISCOVERY_SHARE

Purpose: Familiar/related/discovery mixture; nonnegative shares must sum to 1. Development example: `0.70 / 0.20 / 0.10`. Production: Optional; tested defaults recommended.

## RECOMMENDATION_FAMILIAR_SHARE

Purpose: Familiar/related/discovery mixture; nonnegative shares must sum to 1. Development example: `0.70 / 0.20 / 0.10`. Production: Optional; tested defaults recommended.

## RECOMMENDATION_RELATED_SHARE

Purpose: Familiar/related/discovery mixture; nonnegative shares must sum to 1. Development example: `0.70 / 0.20 / 0.10`. Production: Optional; tested defaults recommended.

## SERVER_PORT

Purpose: Container listener, Compose sets 8080. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SESSION_COOKIE_SECURE

Purpose: false for local HTTP; production forces true. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SIGNAL_CLICK

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_DISLIKE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_HIDE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_LIKE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_MORE_LIKE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_PURCHASE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_READ_PROGRESS

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_SAVE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_SEARCH

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_SHARE

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_SKIP

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SIGNAL_VIEW

Purpose: Actual behavioral ranking weight. Development example: `See .env.example`. Production: Optional; retain tested defaults unless evaluated.

## SMTP_AUTH

Purpose: Local/default SMTP authentication flag; production profile forces true. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SMTP_FROM

Purpose: Approved sending address. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SMTP_HOST

Purpose: Local/default SMTP hostname. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SMTP_PASSWORD

Purpose: Secret credential; unique random value. Required for DB/root or authenticated production SMTP. Never publish.. Development example: `<private-secret>`. Production: Required; secret store or protected private env file.

## SMTP_PORT

Purpose: Local/default SMTP port. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SMTP_STARTTLS

Purpose: Local/default TLS flag; production profile forces STARTTLS required. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## SMTP_USERNAME

Purpose: Authenticated production SMTP username. Development example: `See .env.example / production overlay`. Production: Configure for selected environment; no credentials in documentation.

## Profile and VM options
SPRING_PROFILES_ACTIVE=production is set by the overlay; JAVA_TOOL_OPTIONS controls the production memory percentage and exit-on-OOM behavior. These are real Spring/JVM environment mechanisms. When running the image directly, supply them explicitly. They are fixed values in the overlay rather than `${...}` interpolations. Docker first initializes MYSQL_* only for a new volume: changing an environment password later does not rotate an existing MySQL account. Credential rotation is a separate reviewed operator procedure.

Production needs nonblank DB_PASSWORD, SMTP_USERNAME and SMTP_PASSWORD, secure cookies and a valid HTTPS origin. The guard fails startup otherwise. Run a local example with SESSION_COOKIE_SECURE=false and APP_PUBLIC_URL=http://localhost:8080; do not activate the production profile for that HTTP-only demo. Never expose MySQL or Mailpit to a public network.
