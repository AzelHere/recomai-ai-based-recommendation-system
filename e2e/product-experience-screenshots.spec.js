import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const sizes = [[1366, 768], [1440, 900], [1920, 1080], [2560, 1440]];
const root = path.resolve('design-audit/product-experience');
const targets = [
  ['for-you', 'recommendationsPanel'], ['explore', 'catalogPanel'], ['saved', 'savedPanel'],
  ['my-taste', 'interestProfilePanel'], ['category-preferences', 'preferencesPanel'],
  ['progress', 'progressPanel'], ['activity', 'activityPanel'], ['profile', 'discoverPanel'],
  ['how-it-works', 'howWorksPanel']
];

async function openPanel(page, panelId) {
  if (await page.locator(`#${panelId}`).evaluate(element => element.classList.contains('active'))) return;
  const labels = {
    recommendationsPanel: 'For You', catalogPanel: 'Explore', savedPanel: 'Saved',
    interestProfilePanel: 'My Taste', preferencesPanel: 'Category preferences',
    progressPanel: 'Progress', activityPanel: 'Activity', discoverPanel: 'Profile',
    howWorksPanel: 'How it works'
  };
  const workspaceLink = page.locator('.recommendation-workspace .workspace-nav-link', { hasText: labels[panelId] });
  if (await workspaceLink.isVisible().catch(() => false)) {
    await workspaceLink.click();
  } else {
    const link = page.locator(`.nav-link[data-panel="${panelId}"]`);
    if (!(await link.isVisible())) {
      const menu = page.locator('.secondary-navigation');
      if (!(await menu.evaluate(element => element.open))) await menu.locator('summary').click();
    }
    await link.click();
  }
  await expect(page.locator(`#${panelId}`)).toHaveClass(/active/);
}

async function waitForRecommendationView(page) {
  const workspace = page.locator('#goalFirstDashboard');
  const evidence = page.locator('#recommendationEvidenceNote');
  const fallbackCards = page.locator('#recommendationList .item-card');
  await expect.poll(async () => {
    if (await workspace.isVisible()) return 'workspace';
    if (await evidence.isVisible() && await fallbackCards.count() > 0) return 'catalog-fallback';
    return '';
  }, { timeout: 25_000 }).not.toBe('');
}

test('capture all public and member pages at desktop sizes', async ({ page }) => {
  test.setTimeout(480_000);
  const errors = { page: [], console: [], http: [], failedRequests: [] };
  page.on('pageerror', error => errors.page.push(error.message));
  page.on('console', message => { if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) errors.console.push(message.text()); });
  page.on('requestfailed', request => errors.failedRequests.push(`${request.method()} ${request.url()} ${request.failure()?.errorText}`));
  page.on('response', response => { if (response.status() >= 400 && !(response.status() === 401 && new URL(response.url()).pathname === '/api/users/me')) errors.http.push(`${response.status()} ${response.request().method()} ${response.url()}`); });

  await fs.mkdir(path.join(root, 'screenshots'), { recursive: true });
  await fs.mkdir(path.join(root, 'before'), { recursive: true });
  const baselineNames = ['landing', 'login', 'registration', 'onboarding', 'for-you', 'explore', 'saved', 'my-taste', 'progress', 'activity', 'profile', 'how-it-works', 'category-preferences'];
  for (const [width, height] of sizes) {
    const folder = path.join(root, 'before', `${width}x${height}`);
    await fs.mkdir(folder, { recursive: true });
    for (const name of baselineNames) {
      try { await fs.copyFile(path.resolve(`design-audit/premium-refinement/screenshots/${width}x${height}/${name}.png`), path.join(folder, `${name}.png`)); }
      catch { /* keep baseline gaps visible in the report */ }
    }
  }

  const captured = [];
  const capture = async name => {
    for (const [width, height] of sizes) {
      await page.setViewportSize({ width, height });
      if (name === 'landing') await page.evaluate(() => document.querySelectorAll('#landingPage .reveal').forEach(section => section.classList.add('in-view')));
      await page.waitForTimeout(180);
      const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
      expect(dimensions.document, `${name} horizontal overflow at ${width}×${height}`).toBeLessThanOrEqual(dimensions.viewport);
      const folder = path.join(root, 'screenshots', `${width}x${height}`);
      await fs.mkdir(folder, { recursive: true });
      await page.screenshot({ path: path.join(folder, `${name}.png`), fullPage: true, animations: 'disabled' });
      captured.push(`${width}x${height}/${name}.png`);
    }
  };

  await page.goto('/');
  await expect(page.locator('#heroTitle')).toBeVisible();
  await expect(page.locator('.constellation-field canvas, .constellation-static .constellation-fallback')).toBeVisible();
  await capture('landing');
  await expect(page.locator('#demoResourceGrid .demo-resource-card')).toHaveCount(3);
  await page.locator('[data-demo-interest="programming"]').click();
  await page.locator('[data-demo-interest="design"]').click();
  await expect(page.locator('#demoFeedTitle')).toContainText(/design/i);
  await expect(page.locator('[data-demo-preview-title]')).toHaveText('Map a clearer user journey');
  await expect(page.locator('#demoResourceGrid')).toContainText('user journey');
  await page.locator('[data-demo-interest="design"]').click();
  await expect(page.locator('#demoFeedTitle')).toContainText(/programming/i);
  for (const [mode, name] of [['login', 'login'], ['register', 'registration']]) {
    await page.locator(`[data-auth="${mode}"]`).first().click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await expect(page.locator(mode === 'login' ? '#loginForm' : '#registerForm')).toBeVisible();
    const activeForm = mode === 'login' ? '#loginForm' : '#registerForm';
    expect(await page.locator(`${activeForm} .button-primary`).evaluate(element => getComputedStyle(element).backgroundColor), `${mode} primary action should use orange`).toBe('rgb(201, 135, 101)');
    await capture(name);
    await page.locator('[data-close-auth]').last().click();
  }

  const id = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  await page.locator('[data-auth="register"]').first().click();
  await page.locator('#registerForm [name="displayName"]').fill('Visual Review');
  await page.locator('#registerForm [name="username"]').fill(`visual_${id}`);
  await page.locator('#registerForm [name="email"]').fill(`visual_${id}@example.test`);
  await page.locator('#registerForm [name="password"]').fill('Visual-Review-Password-482!');
  await page.locator('#registerForm button[type="submit"]').click();
  await expect(page.locator('#appShell')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 20_000 });
  await capture('onboarding');
  await page.locator('#onboardingNiche option').nth(1).waitFor({ state: 'attached', timeout: 20_000 });
  await onboardingStage(page,1); await page.locator('#onboardingNiche').selectOption({ index: 1 });
  await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
  await onboardingStage(page,3); await page.locator('#onboardingForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await waitForRecommendationView(page);
  expect(await page.locator('#appShell .button-primary').first().evaluate(element => getComputedStyle(element).backgroundColor), 'workspace primary action should use orange').toBe('rgb(201, 135, 101)');
  expect(await page.locator('#appShell .nav-icon').first().evaluate(element => getComputedStyle(element).color), 'navigation icon should use neutral tones').toBe('rgb(184, 189, 198)');

  for (const [name, panelId] of targets) {
    await openPanel(page, panelId);
    if (panelId === 'progressPanel') await expect(page.locator('.progress-chart-empty')).toBeVisible();
    await capture(name);
  }

  const imageHealth = await page.locator('img').evaluateAll(images => images.map(image => ({
    source: image.currentSrc || image.src,
    loaded: image.complete && image.naturalWidth > 0
  })));
  expect(imageHealth.filter(image => !image.loaded), 'Images should load for captured pages').toEqual([]);

  const result = { sizes, captured, pages: ['landing', 'login', 'registration', 'onboarding', ...targets.map(([name]) => name)], adminAccessible: await page.locator('.admin-nav').isVisible().catch(() => false), errors };
  await fs.writeFile(path.join(root, 'screenshot-results.json'), JSON.stringify(result, null, 2));
  expect(errors.page, 'JavaScript exceptions').toEqual([]);
  expect(errors.console, 'Browser console errors').toEqual([]);
  expect(errors.failedRequests, 'Failed browser requests').toEqual([]);
  expect(errors.http, 'Unexpected HTTP errors').toEqual([]);
  await page.locator('#returnHome').click();
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.demo-resource-card').first()).toHaveCSS('animation-name', 'none');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
});
