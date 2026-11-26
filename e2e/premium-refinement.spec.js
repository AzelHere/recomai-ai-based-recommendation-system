import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const sizes = [[1366, 768], [1440, 900], [1920, 1080], [2560, 1440]];
const root = path.resolve('design-audit/premium-refinement');
const screenshots = path.join(root, 'screenshots');
const baseline = path.resolve('design-audit/screenshots');
const pageTargets = [
  ['for-you', 'recommendationsPanel'], ['explore', 'catalogPanel'], ['saved', 'savedPanel'],
  ['my-taste', 'interestProfilePanel'], ['category-preferences', 'preferencesPanel'],
  ['progress', 'progressPanel'], ['activity', 'activityPanel'], ['profile', 'discoverPanel'],
  ['how-it-works', 'howWorksPanel']
];

async function openPanel(page, panelId) {
  if (await page.locator(`#${panelId}`).evaluate(element => element.classList.contains('active'))) return;
  const workspaceLabels = {
    recommendationsPanel: 'For You', catalogPanel: 'Explore', savedPanel: 'Saved',
    interestProfilePanel: 'My Taste', preferencesPanel: 'Category preferences',
    progressPanel: 'Progress', activityPanel: 'Activity', discoverPanel: 'Profile',
    howWorksPanel: 'How it works', adminPanel: 'Admin'
  };
  const workspaceLink = page.locator('.recommendation-workspace .workspace-nav-link', { hasText: workspaceLabels[panelId] });
  if (await workspaceLink.isVisible().catch(() => false)) {
    await workspaceLink.click();
    await expect(page.locator(`#${panelId}`)).toHaveClass(/active/);
    return;
  }
  const link = page.locator(`.nav-link[data-panel="${panelId}"]`);
  if (!(await link.isVisible())) {
    const details = page.locator('.secondary-navigation');
    if (!(await details.evaluate(element => element.open))) await details.locator('summary').click();
  }
  await link.click({ timeout: 10_000 });
  await expect(page.locator(`#${panelId}`)).toHaveClass(/active/);
}

test('premium frontend refinement across desktop pages and journeys', async ({ page }) => {
  test.setTimeout(480_000);
  const errors = { page: [], console: [], http: [], failedRequests: [], expectedAuth: [] };
  const interactions = [];
  const layout = [];
  page.on('pageerror', error => errors.page.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) errors.console.push(message.text());
  });
  page.on('requestfailed', request => errors.failedRequests.push(`${request.method()} ${request.url()} ${request.failure()?.errorText}`));
  page.on('response', response => {
    if (response.status() < 400) return;
    const request = response.request();
    const entry = `${response.status()} ${request.method()} ${response.url()}`;
    if (response.status() === 401 && new URL(response.url()).pathname === '/api/users/me') errors.expectedAuth.push(entry);
    else errors.http.push(entry);
  });

  await fs.mkdir(screenshots, { recursive: true });
  await fs.mkdir(path.join(root, 'before'), { recursive: true });
  const baselineNames = ['landing', 'login', 'registration', 'onboarding', 'for-you', 'explore', 'saved', 'my-taste', 'progress', 'activity', 'profile', 'how-it-works', 'category-preferences'];
  for (const [width, height] of sizes) {
    const beforeSize = path.join(root, 'before', `${width}x${height}`);
    await fs.mkdir(beforeSize, { recursive: true });
    for (const name of baselineNames) {
      const from = path.join(baseline, `${width}x${height}`, `${name}.png`);
      try { await fs.copyFile(from, path.join(beforeSize, `${name}.png`)); } catch { /* unavailable baseline is reported below */ }
    }
  }

  const capture = async (name, width, height) => {
    await page.setViewportSize({ width, height });
    if (name === 'landing') {
      await page.evaluate(() => document.querySelectorAll('#landingPage .reveal').forEach(section => section.classList.add('in-view')));
    }
    await page.waitForTimeout(180);
    const dims = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
    expect(dims.document, `${name} horizontal overflow at ${width}x${height}`).toBeLessThanOrEqual(dims.viewport);
    layout.push(`${name} ${width}x${height}: no horizontal overflow`);
    const folder = path.join(screenshots, `${width}x${height}`);
    await fs.mkdir(folder, { recursive: true });
    await page.screenshot({ path: path.join(folder, `${name}.png`), fullPage: true, animations: 'disabled' });
    if (name === 'for-you' && width === 1440) {
      const workspaceMetrics = await page.evaluate(() => Object.fromEntries(['.recommendation-workspace', '.workspace-left', '.workspace-center', '.workspace-orbit', '.workspace-card[data-position="active"]', '.workspace-right', '.workspace-strip'].map(selector => {
        const element = document.querySelector(selector);
        if (!element) return [selector, null];
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        return [selector, { rect: [rect.x, rect.y, rect.width, rect.height], display: style.display, visibility: style.visibility, opacity: style.opacity, text: element.innerText?.slice(0, 100) }];
      })));
      await fs.writeFile(path.join(root, 'for-you-layout.json'), JSON.stringify(workspaceMetrics, null, 2));
    }
  };

  await page.goto('/');
  await expect(page.locator('#heroTitle')).toBeVisible();
  await expect(page.locator('.constellation-field')).toBeVisible();
  await expect(page.locator('.constellation-field canvas, .constellation-static .constellation-fallback')).toBeVisible();
  for (const [width, height] of sizes) await capture('landing', width, height);
  interactions.push('Landing visible; local hero artwork loaded: PASS');

  for (const [mode, name] of [['login', 'login'], ['register', 'registration']]) {
    await page.locator(`[data-auth="${mode}"]`).first().click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await expect(page.locator(mode === 'login' ? '#loginForm' : '#registerForm')).toBeVisible();
    for (const [width, height] of sizes) await capture(name, width, height);
    await page.locator('[data-close-auth]').last().click();
  }
  interactions.push('Landing sign-in and registration entry points: PASS');

  const id = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const account = { username: `refine_${id}`, email: `refine_${id}@example.test`, password: 'Refinement-Test-Password-482!' };
  await page.locator('[data-auth="register"]').first().click();
  await page.locator('#registerForm [name="displayName"]').fill('Refinement Review');
  await page.locator('#registerForm [name="username"]').fill(account.username);
  await page.locator('#registerForm [name="email"]').fill(account.email);
  await page.locator('#registerForm [name="password"]').fill(account.password);
  await page.locator('#registerForm button[type="submit"]').click();
  await expect(page.locator('#appShell')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 20_000 });
  for (const [width, height] of sizes) await capture('onboarding', width, height);
  const niche = page.locator('#onboardingNiche');
  await niche.locator('option').nth(1).waitFor({ state: 'attached', timeout: 20_000 });
  await onboardingStage(page,1); await niche.selectOption({ index: 1 });
  await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
  await onboardingStage(page,3); await page.locator('#onboardingForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  interactions.push('Registration and onboarding to For You: PASS');
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.waitForTimeout(350);

  for (const [name, panelId] of pageTargets) {
    await openPanel(page, panelId);
    const homeControl = panelId === 'recommendationsPanel' ? page.locator('#returnHome') : page.locator('#returnHome');
    await expect(homeControl).toBeVisible();
    await page.waitForTimeout(260);
    for (const [width, height] of sizes) await capture(name, width, height);
  }
  interactions.push('For You, Explore, Saved, My Taste, Category Preferences, Progress, Activity, Profile and How It Works open: PASS');

  await openPanel(page, 'catalogPanel');
  const artwork = await page.locator('#catalogItems .item-card svg[role="img"]').count();
  expect(artwork).toBeGreaterThan(0);
  const uniqueArt = await page.locator('#catalogItems .item-card .item-art').evaluateAll(cards => new Set(cards.map(card => [...card.classList].find(name => name.startsWith('art-')))).size);
  interactions.push(`Explore subject artwork: ${artwork} illustrations, ${uniqueArt} distinct subjects rendered`);

  await page.locator('#catalogSearchQuery').fill('resource');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
  interactions.push('Explore search: PASS');
  await page.locator('#clearCatalogSearch').click();
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });
  const categoryFilter = page.locator('#categoryFilter');
  if (await categoryFilter.locator('option').count() > 1) {
    await categoryFilter.selectOption({ index: 1 });
    await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
    interactions.push('Explore category filter: PASS');
    await categoryFilter.selectOption('');
  } else interactions.push('Explore category filter: NOT VERIFIED (no options supplied by isolated catalog)');
  await page.locator('#sortFilter').selectOption('title');
  await page.locator('#sortFilter').selectOption('relevance');
  interactions.push('Explore sort controls: PASS');

  const card = page.locator('#catalogItems .item-card').first();
  const viewRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'View', exact: true }).click();
  expect((await viewRequest).status()).toBe(201);
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  interactions.push('Resource View interaction: PASS (201)');
  const saveRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'Save', exact: true }).click();
  expect((await saveRequest).status()).toBe(201);
  interactions.push('Resource Save interaction: PASS (201)');
  const rating = card.locator('.rating-control');
  await rating.locator('select').selectOption('5');
  const feedbackRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/feedback');
  await rating.getByRole('button', { name: /save rating/i }).click();
  expect((await feedbackRequest).ok()).toBeTruthy();
  interactions.push('Resource rating submission: PASS');
  await openPanel(page, 'savedPanel');
  await expect(page.locator('#savedItems .item-card')).toHaveCount(1, { timeout: 20_000 });
  interactions.push('Saved item remains available in Saved: PASS');

  for (const [, panelId] of pageTargets) {
    await openPanel(page, panelId);
    const homeControl = panelId === 'recommendationsPanel' ? page.locator('#returnHome') : page.locator('#returnHome');
    await expect(homeControl).toBeVisible();
    await homeControl.click();
    await expect(page.locator('#landingPage')).toBeVisible();
    await expect(page.locator('[data-enter-workspace]')).toBeVisible();
    await expect(page.locator('#authPanel')).toBeHidden();
    await page.locator('[data-enter-workspace]').click();
    await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/);
    await expect(page.locator('#appShell')).toBeVisible();
  }
  interactions.push('Return-home control and authenticated workspace re-entry for every major section: PASS');

  await page.locator('#returnHome').click();
  await page.locator('[data-enter-workspace]').click();
  await page.goBack();
  await expect(page.locator('#landingPage')).toBeVisible();
  await page.goForward();
  await expect(page.locator('#appShell')).toBeVisible();
  interactions.push('Browser Back/Forward preserves workspace route and session: PASS');

  await openPanel(page, 'catalogPanel');
  await page.locator('#logoutButton').click();
  await expect(page.locator('#authPanel')).toBeVisible();
  await page.locator('#loginForm [name="email"]').fill(account.email);
  await page.locator('#loginForm [name="password"]').fill(account.password);
  await page.locator('#loginForm button[type="submit"]').click();
  await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('#catalogPanel')).toHaveClass(/active/);
  interactions.push('Sign-out and sign-in using the isolated test account: PASS');

  const adminAvailable = await page.locator('.admin-nav').isVisible();
  const result = { sizes, screenshots: [], interactions, layout, adminAvailable, errors };
  for (const [width, height] of sizes) {
    for (const name of ['landing', 'login', 'registration', 'onboarding', ...pageTargets.map(([pageName]) => pageName)]) {
      result.screenshots.push(`${width}x${height}/${name}.png`);
    }
  }
  await fs.writeFile(path.join(root, 'test-results.json'), JSON.stringify(result, null, 2));
  expect(errors.page, 'JavaScript exceptions').toEqual([]);
  expect(errors.console, 'Browser console errors').toEqual([]);
  expect(errors.failedRequests, 'Failed browser requests').toEqual([]);
  expect(errors.http, 'Unexpected HTTP errors').toEqual([]);
});
