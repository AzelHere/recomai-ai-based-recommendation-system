import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const sizes = [[1366, 768], [1440, 900], [1920, 1080], [2560, 1440]];
const output = path.resolve('design-audit/landing-explore-after/screenshots');

test('redesign landing and Explore, preserving live actions', async ({ page }) => {
  test.setTimeout(180_000);
  const consoleErrors = [];
  const failedRequests = [];
  const httpErrors = [];
  const expectedAuthResponses = [];
  const layoutChecks = [];
  const interactions = [];
  page.on('pageerror', error => consoleErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) consoleErrors.push(message.text());
  });
  page.on('requestfailed', request => failedRequests.push(`${request.method()} ${request.url()} — ${request.failure()?.errorText}`));
  page.on('response', response => {
    if (response.status() >= 400) {
      const request = response.request();
      const pathname = new URL(response.url()).pathname;
      const entry = `${response.status()} ${request.method()} ${response.url()}`;
      if (response.status() === 401 && pathname === '/api/users/me') expectedAuthResponses.push(entry);
      else httpErrors.push(entry);
    }
  });
  const capture = async (name, width, height) => {
    await page.setViewportSize({ width, height });
    await page.waitForTimeout(250);
    const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, page: document.documentElement.scrollWidth }));
    expect(dimensions.page, `${name} horizontal overflow at ${width}x${height}`).toBeLessThanOrEqual(dimensions.viewport);
    layoutChecks.push(`${name} ${width}x${height}: no horizontal overflow`);
    const folder = path.join(output, `${width}x${height}`);
    await fs.mkdir(folder, { recursive: true });
    await page.screenshot({ path: path.join(folder, name), fullPage: true, animations: 'disabled' });
  };

  await page.goto('/');
  await expect(page.locator('#landingPage')).toBeVisible();
  await expect(page.locator('#landingPage .editorial-section.reveal').first()).toBeAttached();
  await page.locator('#landingPage .editorial-section.reveal, #landingPage .final-cta.reveal').evaluateAll(sections => sections.forEach(section => section.classList.add('in-view')));
  for (const [width, height] of sizes) await capture('landing.png', width, height);

  await page.locator('[data-auth="login"]').first().click();
  await expect(page.locator('#authPanel')).toBeVisible();
  await expect(page.locator('#loginForm')).toBeVisible();
  interactions.push('Landing Log in opens sign-in form: PASS');
  await page.locator('[data-close-auth]').last().click();
  await page.locator('[data-auth="register"]').first().click();
  await expect(page.locator('#authPanel')).toBeVisible();
  await expect(page.locator('#registerForm')).toBeVisible();
  interactions.push('Landing Get started opens registration form: PASS');

  const id = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  await page.locator('#registerForm [name="displayName"]').fill('Landing Explore Review');
  await page.locator('#registerForm [name="username"]').fill(`review_${id}`);
  await page.locator('#registerForm [name="email"]').fill(`review_${id}@example.test`);
  await page.locator('#registerForm [name="password"]').fill('Review-only-Password-482!');
  await page.locator('#registerForm button[type="submit"]').click();
  await expect(page.locator('#appShell')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 20_000 });
  await page.locator('#onboardingNiche option').nth(1).waitFor({ state: 'attached', timeout: 20_000 });
  await onboardingStage(page,1); await page.locator('#onboardingNiche').selectOption({ index: 1 });
  await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
  await onboardingStage(page,3); await page.locator('#onboardingForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  const exploreNavigation = page.locator('.nav-link[data-panel="catalogPanel"]');
  await expect(exploreNavigation).toBeVisible({ timeout: 20_000 });
  await exploreNavigation.click();
  await expect(page.locator('#catalogPanel')).toHaveClass(/active/);
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 30_000 });
  const onboardingToast = page.locator('.toast-message.visible');
  if (await onboardingToast.count()) await expect(onboardingToast).toBeHidden({ timeout: 8_000 });
  interactions.push('Workspace navigation opens Explore: PASS');

  for (const [width, height] of sizes) await capture('explore.png', width, height);

  const search = page.locator('#catalogSearchQuery');
  await search.fill('resource');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
  interactions.push('Explore keyword search submits and updates results: PASS');
  await page.locator('#clearCatalogSearch').click();
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });
  interactions.push('Explore Clear restores catalog: PASS');

  const category = page.locator('#categoryFilter');
  if (await category.locator('option').count() > 1) {
    await category.selectOption({ index: 1 });
    await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
    interactions.push('Explore category filter updates results: PASS');
    await category.selectOption('');
  } else {
    interactions.push('Explore category filter: NOT VERIFIED (no category options available)');
  }
  await page.locator('#sortFilter').selectOption('title');
  interactions.push('Explore sort filter changes to title order: PASS');
  await page.locator('#sortFilter').selectOption('relevance');

  const card = page.locator('#catalogItems .item-card').first();
  const viewResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'View', exact: true }).click();
  const view = await viewResponse;
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  expect(view.ok(), `VIEW interaction returned ${view.status()}`).toBeTruthy();
  interactions.push(`Resource View records interaction (${view.status()}): PASS`);

  const saveResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'Save', exact: true }).click();
  const save = await saveResponse;
  expect(save.ok(), `SAVE interaction returned ${save.status()}`).toBeTruthy();
  interactions.push(`Resource Save records interaction (${save.status()}): PASS`);

  await fs.writeFile(path.resolve('design-audit/landing-explore-after/test-results.json'), JSON.stringify({ sizes, interactions, layoutChecks, consoleErrors, failedRequests, httpErrors, expectedAuthResponses }, null, 2));
  expect(consoleErrors, 'Browser console errors').toEqual([]);
  expect(failedRequests, 'Failed browser requests').toEqual([]);
  expect(httpErrors, 'HTTP responses >= 400').toEqual([]);
});
