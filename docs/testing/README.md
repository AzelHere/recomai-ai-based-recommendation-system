# Recom AI testing

Run from the repository root with Java 21, Node.js and Docker Compose:

```powershell
.\mvnw.cmd -B -f backend/pom.xml clean verify
npm ci
npx playwright install chromium
npm run test:e2e -- --workers=1
npm run test:e2e -- --workers=2
```

On Linux use `sh mvnw`. Windows uses installed Edge by default; set `PLAYWRIGHT_CHANNEL=chromium` to use Playwright's Chromium. The isolated runner creates a unique Compose project, database and local ports. It never uses the development database. Containers stop afterwards; volumes are retained, not deleted. See [coverage and final results](../code-quality/README.md).

JaCoCo 0.8.14 runs with `verify`; open `backend/target/site/jacoco/index.html`. It covers the Maven JVM tests, not browser execution inside Docker. No important packages are excluded and no artificial coverage threshold is claimed.

Static analysis (portable container invocation avoids the Windows-space-path plugin issue):

```sh
docker run --rm -v "$PWD:/workspace" -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -f backend/pom.xml compile com.github.spotbugs:spotbugs-maven-plugin:4.10.5.0:spotbugs
```

The explicit screenshot test is reproducible with `PUBLICATION_CAPTURE=1` and `npm run test:e2e -- e2e/publication-gallery.spec.js`; it uses a generated isolated demonstration administrator and licensed metadata. Default runs skip optional historical visual/render measurements and this capture-only case. Passing workflow behavior does not prove ranking quality or production capacity.
