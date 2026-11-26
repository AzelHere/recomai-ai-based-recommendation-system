# Recom AI local setup and catalog initialization

Start from a clean clone using the root README. `start-local.ps1` generates unique database secrets. For manual setup, copy `.env.example`, replace the two password placeholders and start `docker compose --profile local-mail up --build --wait`. The MySQL volume is persistent; never delete an existing volume to fix startup.

Register a demonstration account at http://localhost:8080/. There is no published administrator password. To provision your own local account, use the local operator database console:

```sh
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

At the MySQL prompt, substitute the exact username you registered (not an email or another person's account):

```sql
UPDATE users SET role='ADMIN', auth_version=auth_version+1
WHERE username='your_registered_demo_username';
SELECT ROW_COUNT();
```

Expect exactly one affected account. Sign out and sign in again. This is an explicit local operator step, not a public role-escalation endpoint. Application administrators must be provisioned by an authorized operator in production.

Open Admin → Import → Verified-source imports. Select `data/processed/educational-resources.json`, preview the decisions and commit. The UI splits large files into batches of at most 250; 1,665 imports are accepted and 105 uncertain editions held. Then preview/commit `data/processed/educational-articles.json` for 500 readings. Fresh startup has 200 curated links; the final total is 2,365, including 505 ARTICLE resources. Exact retries do not duplicate imports. Review holds are intentionally not auto-merged.

Imported metadata carries source, external ID, canonical URL, license, attribution and metadata-origin notes. Availability checks are time-bound; import is not a new live URL verification. Article acquisition receipts are available in `data/manifests/article-acquisition.json`. Reacquisition is optional, explicit and described in [data/README.md](../data/README.md).

Troubleshooting: inspect `docker compose ps`, `/api/health` and bounded backend logs. If a port is occupied, change APP_HOST_PORT, MYSQL_HOST_PORT or MAILPIT_HOST_PORT and update APP_PUBLIC_URL consistently. After editing source, rebuild. Ordinary restarts use `docker compose up --wait`. If credentials differ from an existing volume, restore the original credentials; changing `.env` does not reset MySQL users already stored in that volume. Production uses the separate overlay and operational checks in [the deployment guide](deployment/Deployment-Guide.md).
