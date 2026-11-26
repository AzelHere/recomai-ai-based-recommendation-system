# Security of Recom AI

Do not disclose passwords, reset links, session cookies or private database records in public issues. Report a suspected vulnerability privately through GitHub's security reporting feature if enabled, or contact the repository owner privately.

The application rotates sessions at sign-in, checks persisted authentication versions, scopes personal data to its owner, uses parameterized JDBC queries, and checks same-origin mutations. Passwords are salted and hashed; reset tokens expire and delivery failures invalidate them. Catalog acquisition validates public DNS destinations at connection time and restricts providers, redirects, response sizes and timeouts.

Local demonstration readiness does not establish public production readiness. Production requires trusted HTTPS, secure cookies, real SMTP credentials, least-privilege database credentials, operator-reviewed administrator provisioning and tested off-host backups. Sessions, rate limits and caches are process-local; horizontal scaling requires additional coordination. Historical dependency scans are time-bound and do not certify future images. See [deployment](docs/deployment/Deployment-Guide.md) and [quality findings](docs/code-quality/README.md).
