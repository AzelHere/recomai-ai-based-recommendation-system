import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const playwrightCli = path.join(projectRoot, 'node_modules', '@playwright', 'test', 'cli.js');

if (!existsSync(playwrightCli)) {
  console.error('Playwright is not installed. Run npm install before the E2E test.');
  process.exit(1);
}

function run(command, args, env) {
  const result = spawnSync(command, args, { cwd: projectRoot, env, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with status ${result.status ?? 'unknown'}`);
}

async function reserveLocalPorts(count) {
  const servers = [];
  try {
    for (let index = 0; index < count; index += 1) {
      const server = net.createServer();
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
      });
      servers.push(server);
    }
    return servers.map(server => server.address().port);
  } finally {
    await Promise.all(servers.map(server => new Promise(resolve => server.close(resolve))));
  }
}

const runId = `${Date.now()}-${randomBytes(3).toString('hex')}`;
const projectName = `recomai-e2e-${runId}`;
const databaseName = `recomai_e2e_${runId.replaceAll('-', '_')}`;
const [appPort, mysqlPort, mailpitPort] = await reserveLocalPorts(3);
const composeEnv = {
  ...process.env,
  MYSQL_DATABASE: databaseName,
  APP_HOST_PORT: String(appPort),
  MYSQL_HOST_PORT: String(mysqlPort),
  MAILPIT_HOST_PORT: String(mailpitPort),
  COMPOSE_PROFILES: 'local-mail',
  APP_PUBLIC_URL: `http://127.0.0.1:${appPort}`,
  PASSWORD_RESET_COOLDOWN_SECONDS: '0'
};

let failed = false;
try {
  run('docker', ['compose', ...(process.env.E2E_EXISTING_IMAGE ? ['-f','docker-compose.yml','-f','docker-compose.audit-image.yml'] : []), '-p', projectName, 'up', '--detach', ...(process.env.E2E_EXISTING_IMAGE ? ['--no-build'] : ['--build']), '--wait'], composeEnv);
  const testEnv = {
    ...composeEnv,
    E2E_BASE_URL: `http://127.0.0.1:${appPort}`,
    E2E_MAILPIT_URL: `http://127.0.0.1:${mailpitPort}`,
    E2E_COMPOSE_PROJECT: projectName
  };
  const selection=process.argv.slice(2);
  testEnv.E2E_FULL_SUITE=String(selection.length===0 || selection.every(arg=>/^--workers=\d+$/.test(arg)));
  // Concurrent audit runs must not delete each other's traces in Playwright's output cleanup.
  run(process.execPath, [playwrightCli, 'test', '--output', path.join('test-results', projectName), ...process.argv.slice(2)], testEnv);
} catch (error) {
  failed = true;
  console.error(error.message);
  try {
    console.error('Isolated backend logs for diagnosis:');
    run('docker', ['compose', '-p', projectName, 'logs', '--since', '5m', '--no-color', 'backend'], composeEnv);
  } catch {
    // The backend may not have started if Compose setup itself failed.
  }
} finally {
  try {
    if (process.env.E2E_COVERAGE_DIR) {
      if (!process.env.E2E_EXISTING_IMAGE) throw new Error('Coverage collection requires an explicitly instrumented test image.');
      const coverageDirectory = path.resolve(projectRoot, process.env.E2E_COVERAGE_DIR);
      if (!coverageDirectory.startsWith(projectRoot + path.sep)) throw new Error('Coverage output must stay inside the workspace.');
      mkdirSync(coverageDirectory, { recursive: true });
      // The agent flushes on graceful shutdown; retain the stopped container until its file is copied.
      run('docker', ['compose', '-p', projectName, 'stop', 'backend'], composeEnv);
      run('docker', ['cp', `${projectName}-backend-1:/tmp/jacoco-e2e.exec`, path.join(coverageDirectory, `${projectName}.exec`)], composeEnv);
    }
  } catch (error) {
    failed = true;
    console.error(`Coverage collection failed: ${error.message}`);
  }
  try {
    run('docker', ['compose', '-p', projectName, 'down', '--remove-orphans'], composeEnv);
    console.log(`Stopped isolated E2E containers and retained their MySQL volume (${projectName}_mysql_data).`);
  } catch (error) {
    failed = true;
    console.error(`E2E cleanup failed for isolated project ${projectName}: ${error.message}`);
  }
}

if (failed) process.exitCode = 1;
