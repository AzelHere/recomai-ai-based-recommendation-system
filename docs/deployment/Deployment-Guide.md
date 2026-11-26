# Deployment guide

## Status and prerequisites
PUBLIC DEPLOYMENT BLOCKED until secrets/admin, real HTTPS/SMTP, residual-vulnerability review, lawful content use and encrypted off-host backup gates are satisfied. This guide prepares deployment; no server was purchased or publicly deployed. Local university demonstration remains supported. Use Java 21 and the Maven wrapper for host work; Docker Desktop on Windows or Docker Engine with the Compose plugin on Linux. Compose must support the production overlay's `!reset` tag (2.24.4 or later). The inspected local Compose version is recorded in evidence. A proposed server budget is 4 GB RAM and at least 40 GB SSD; confirm actual load before launch.

## Windows PowerShell local startup — verified
From the repository root:
```powershell
docker compose up -d
Invoke-RestMethod http://localhost:8080/api/health
```
Open http://localhost:8080. Use `docker compose --profile local-mail up -d` for development reset email capture at http://localhost:8025. Existing local .env remains private. For a fresh checkout run `./start-local.ps1` to generate local random credentials; inspect that helper before use. It is not a production secret manager.

After source changes rebuild with `docker compose up -d --build`. The Docker build executes Maven clean verify. Rebuilding is unnecessary for ordinary restarts. This phase rebuilt only the image affected by a compatible OS package fix/removal of an unused base-image helper. Clean builds need more disk/network time than cached restarts. Do not delete the Maven/BuildKit cache routinely to speed startup.

## Linux server setup — prepared, not publicly executed
Use an operator-approved Linux host and Docker installation from official documentation. After repository review and publishing approval, clone the chosen repository revision. Do not copy the developer's private database or .env. Create a protected private production environment file with all required values from Environment-Reference.md, unique database secrets, SMTP credentials and the real HTTPS origin. Disable local-mail and restrict SSH/firewall access. Verify filesystem permissions with the host administrator.

```bash
# Linux syntax; illustrative paths, requires real private environment values
chmod 600 /srv/recomai/production.env
docker compose --env-file /srv/recomai/production.env -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose --env-file /srv/recomai/production.env -f docker-compose.yml -f docker-compose.production.yml up -d --build
curl --fail http://127.0.0.1:8080/api/health
```
These production commands have structural/local verification only; they are not evidence of a live public deployment. Keep the app's host port bound to 127.0.0.1. The production overlay removes the MySQL host mapping. Compose creates a persistent named volume and starts the backend only after database health succeeds. Flyway validates and migrates in place; never use clean/baseline/reset to bypass an error. The application account owns this one schema so it can run Flyway DDL; it is not a least-privilege runtime-only credential. A future split migration/runtime account would need an explicitly designed deployment process.

## Fresh educational catalog and attribution
On a new database Flyway plus the idempotent startup loader initializes 200 curated resources. Importing the 1,665 genuine provider records is an explicit operator task. Build the Java CLI with Java 21 and Maven, then run the existing acquisition pipeline (review data/README.md). Windows:
```powershell
./mvnw.cmd -f backend/pom.xml compile dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$catalogClasspath = 'backend/target/classes;' + (Get-Content backend/target/runtime-classpath.txt -Raw).Trim()
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.RealDataAcquisition data
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.ValidateSourceSample data
```
Linux Java classpaths use `:` instead of `;`. The acquisition tool uses official sources, license/provenance capture and bounded network access. Recheck provider permission, robots policy and availability at acquisition time; this phase reused prior genuine acquisition evidence rather than repeatedly fetching source metadata.

Register a new operator account with a private password. Through a trusted MySQL administrative client, first SELECT the exact account by email, then perform the reviewed `UPDATE users SET role='ADMIN' WHERE user_id=<reviewed-id>` and verify one row changed. This template is not executed automatically. Sign out/in to refresh the role. Never grant users admin through the browser or ship an admin password/hash. Use Admin → Import → Verified-source imports, preview processed/educational-resources.json, review accepted/skipped/held decisions, then commit the matching preview. UI batches at most 250 records within the server's 500-record/3 MB bound. Retain provenance/attributions. Uncertain editions remain held. No private user dump is needed or shipped.

## HTTPS and networking
Terminate TLS on a host reverse proxy forwarding only to 127.0.0.1:8080; `docs/deployment/nginx-recomai.conf.example` is a reviewed template. Replace server_name and certificate paths, provision a trusted certificate, verify HTTP-to-HTTPS redirect, HSTS and Secure/HttpOnly/SameSite cookies using a real browser. Strip incoming Forwarded/X-Forwarded-* values at the proxy. The application does not rely on arbitrary forwarded headers for authorization and no broad trust of client-supplied proxies is enabled. PRODUCTION_PUBLIC_URL supplies the trusted origin/reset links. Allow only public ports 80/443 and reviewed SSH; never MySQL/Mailpit. Proxy configuration is not a live TLS test.

## Backup and recovery
Follow Backup-Recovery.md. Before every update create and verify an encrypted logical backup and preserve the previous image ID, environment reference and migration version. Application image rollback is safe only if the older code is compatible with the migrated schema. Flyway does not automatically reverse migrations. If incompatible, restore a compatible backup into a separate reviewed database, verify it, then plan cutover; do not overwrite a valuable volume as a troubleshooting shortcut.

## Updates and operational checks
Review source, dependency/OS scan, Maven verify, isolated browser tests and migration compatibility before building. Tag the reviewed image with a version/digest; do not deploy mutable latest without recording its ID. Run `docker compose ps`, health, login, recommendations and a journey smoke check. Monitor status, disk, reset-mail delivery and sanitized request logs. In-memory sessions can end on restart; this is an explicit single-instance limitation.

## Shutdown and troubleshooting
`docker compose stop` stops services and keeps containers/volumes. `docker compose down` removes this project's containers/network while preserving the named database volume. Never add `-v`. On connection failure inspect `docker compose ps` and bounded logs privately. Verify DB health, environment variable names and available disk before rebuilding. A 503 is service unavailability, not an empty catalog. On an invalid Flyway checksum compare the original applied migration; do not edit history or reset the database. On cookie failure distinguish HTTP local cookies from HTTPS production cookies. A provider link failure does not imply database loss. Review ports for conflicts rather than deleting another project.
