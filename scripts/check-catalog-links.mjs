import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const timeoutMs = Number(process.env.CATALOG_LINK_TIMEOUT_MS || 10_000);
const concurrency = Math.max(1, Number(process.env.CATALOG_LINK_CONCURRENCY || 6));

const [seedTsv, initialMigration, urlCorrections] = await Promise.all([
  readFile(path.join(root, 'backend/src/main/resources/catalog/curated-resources.tsv'), 'utf8'),
  readFile(path.join(root, 'backend/src/main/resources/db/migration/V3__adaptive_interest_discovery.sql'), 'utf8'),
  readFile(path.join(root, 'backend/src/main/resources/db/migration/V8__repair_broken_catalog_urls.sql'), 'utf8')
]);

const urls = new Map();
const supersededUrls = new Set(
  [...urlCorrections.matchAll(/WHEN\s+'((?:https?):\/\/[^']+)'\s+THEN\s+'((?:https?):\/\/[^']+)'/g)]
    .map(([, oldUrl]) => oldUrl)
);
for (const [index, line] of seedTsv.split(/\r?\n/).entries()) {
  if (index === 0 || !line.trim()) continue;
  const columns = line.split('\t');
  const url = columns[2]?.trim();
  if (url) urls.set(url, `curated-resources.tsv:${index + 1}`);
}

const seedBlock = initialMigration.match(/INSERT INTO items[\s\S]*?FROM\s*\(([\s\S]*?)\)\s*seed/i)?.[1] || '';
for (const [index, match] of [...seedBlock.matchAll(/'((?:https?):\/\/[^']+)'/g)].entries()) {
  const url = match[1];
  if (!urls.has(url)) urls.set(url, `V3__adaptive_interest_discovery.sql:${index + 1}`);
}

if (process.env.CATALOG_API_URL) {
  try {
    const response = await fetch(process.env.CATALOG_API_URL, { signal: AbortSignal.timeout(timeoutMs) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const catalog = await response.json();
    for (const item of catalog) {
      if (item.externalUrl && !supersededUrls.has(item.externalUrl)) {
        urls.set(item.externalUrl, `catalog API: ${item.title || item.itemId || 'resource'}`);
      }
    }
  } catch (error) {
    console.error(`Could not load runtime catalog from CATALOG_API_URL: ${error.message}`);
    process.exitCode = 1;
  }
}

for (const oldUrl of supersededUrls) urls.delete(oldUrl);

async function check(url) {
  let headStatus = 'ERROR';
  let headError = '';
  try {
    const response = await fetch(url, {
      method: 'HEAD',
      redirect: 'follow',
      signal: AbortSignal.timeout(timeoutMs),
      headers: { 'user-agent': 'RecomAI-Catalog-Link-Check/1.0' }
    });
    headStatus = response.status;
    if (response.ok) return { status: response.status, method: 'HEAD', note: '' };
    headError = `HEAD ${response.status}`;
  } catch (error) {
    headError = error.name === 'TimeoutError' ? `HEAD timed out after ${timeoutMs}ms` : `HEAD ${error.message}`;
  }

  try {
    const response = await fetch(url, {
      method: 'GET',
      redirect: 'follow',
      signal: AbortSignal.timeout(timeoutMs),
      headers: { range: 'bytes=0-0', 'user-agent': 'RecomAI-Catalog-Link-Check/1.0' }
    });
    return { status: response.status, method: 'GET', note: response.ok ? `fallback after ${headError}` : headError };
  } catch (error) {
    const reason = error.name === 'TimeoutError' ? `GET timed out after ${timeoutMs}ms` : `GET ${error.message}`;
    return { status: headStatus, method: 'HEAD → GET', note: `${headError}; ${reason}` };
  }
}

const entries = [...urls.entries()];
const results = new Array(entries.length);
let next = 0;
await Promise.all(Array.from({ length: Math.min(concurrency, entries.length) }, async () => {
  while (next < entries.length) {
    const index = next++;
    const [url, source] = entries[index];
    results[index] = { source, url, ...(await check(url)) };
  }
}));

const widths = { source: 39, method: 12, status: 8 };
const header = `${'SOURCE'.padEnd(widths.source)} ${'METHOD'.padEnd(widths.method)} ${'STATUS'.padEnd(widths.status)} URL  NOTE`;
console.log(header);
console.log('-'.repeat(header.length));
for (const result of results) {
  console.log(`${result.source.slice(0, widths.source).padEnd(widths.source)} ${result.method.padEnd(widths.method)} ${String(result.status).padEnd(widths.status)} ${result.url}  ${result.note}`);
}
const failed = results.filter(result => !(result.status >= 200 && result.status < 400));
console.log(`\nChecked ${results.length} unique catalog URLs; ${results.length - failed.length} returned 2xx/3xx and ${failed.length} need review.`);
if (failed.length) process.exitCode = 1;
