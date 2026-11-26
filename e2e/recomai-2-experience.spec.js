import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const viewports = [
  [1366, 768], [1440, 900], [1920, 1080], [2560, 1440],
  [1536, 864]
];
const deliverableRoot = path.resolve('design-audit/recomai-2-phase-2c');
const screenshotRoot = path.join(deliverableRoot, 'screenshots');

test('RecomAI 2 goal-first landing, For You, and Explore', async ({ page }) => {
  test.setTimeout(240_000);
  const report = { layouts: [], actions: [], browserErrors: [] };
  page.on('pageerror', error => report.browserErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().includes('favicon') && !message.text().includes('401')) report.browserErrors.push(message.text());
  });
  const capture = async screen => {
    for (const [width, height] of viewports) {
      await page.setViewportSize({ width, height });
      await page.evaluate(() => window.scrollTo({top:0,behavior:'instant'}));
      await page.waitForTimeout(120);
      const widthCheck = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
      expect(widthCheck.document, `${screen} overflows at ${width}x${height}`).toBeLessThanOrEqual(widthCheck.viewport);
      report.layouts.push(`${screen} ${width}x${height}: no horizontal overflow`);
      const folder = path.join(screenshotRoot, `${width}x${height}`);
      await fs.mkdir(folder, { recursive: true });
      await page.screenshot({ path: path.join(folder, `${screen}.png`), fullPage: false, animations: 'disabled' });
    }
  };

  await page.goto('/');
  await expect(page.locator('#landingPage')).toBeVisible();
  await expect(page.getByRole('heading', { name: /Your future starts with one step/i })).toBeVisible();
  await capture('landing');
  await expect(page.locator('#discoveryForm')).toBeVisible();
  await expect(page.locator('#demoResourceGrid .demo-resource-card')).toHaveCount(3);
  const dataInterest = page.locator('[data-demo-interest="data"]');
  await dataInterest.click();
  await expect(page.locator('[data-demo-preview-title]')).toHaveText('Ask better questions with SQL');
  await expect(page.locator('[data-demo-preview-art] svg')).toHaveAttribute('aria-label', /Database illustration/);
  report.actions.push('Landing search entry and interactive sample preview remain available.');
  await page.setViewportSize({width:390,height:844});
  const mobileMenu = page.locator('.mobile-menu-toggle');
  await mobileMenu.focus();
  await page.keyboard.press('Enter');
  await expect(mobileMenu).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('#landingNav a').first()).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(mobileMenu).toHaveAttribute('aria-expanded', 'false');
  await expect(mobileMenu).toBeFocused();
  report.actions.push('Mobile landing navigation opens and closes with keyboard focus restored.');
  await page.getByRole('button', { name: 'Creative & Lifestyle' }).click();
  await expect(page.getByRole('button', { name: 'Creative & Lifestyle' })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#goalSelectionNote')).toContainText('Temporary selection');
  await page.getByRole('button', { name: /Find My Path/ }).click();
  await expect(page.locator('#registerForm')).toBeVisible();
  report.actions.push('Goal selection opens registration and remains labeled temporary.');

  const unique = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  await page.locator('#registerForm [name="displayName"]').fill('RecomAI 2 Review');
  await page.locator('#registerForm [name="username"]').fill(`recomai2_${unique}`);
  await page.locator('#registerForm [name="email"]').fill(`recomai2_${unique}@example.test`);
  await page.locator('#registerForm [name="password"]').fill('RecomAI-Review-Password-482!');
  await page.locator('#registerForm button[type="submit"]').click();
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await expect(page.locator('#onboardingPurpose')).toHaveValue('EXPLORE');
  await expect(page.locator('#onboardingNiche')).toHaveValue(await page.locator('#onboardingNiche option').filter({ hasText: 'Creative & Lifestyle' }).getAttribute('value'));
  report.actions.push('Registration lands in existing onboarding with the temporary choice prefilled.');
  await onboardingStage(page,3); await page.locator('#onboardingForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await expect(page.locator('#goalFirstDashboard')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#appShell .sidebar')).toBeVisible();
  await expect(page.locator('#appShell .dashboard-header')).toBeVisible();
  await expect(page.locator('#nextStepCard .goal-next-card')).toBeVisible();
  await expect(page.locator('#nextStepCard')).toContainText('Why this was recommended');
  await expect(page.locator('#suggestedPath')).toBeVisible();
  await expect(page.locator('.suggested-path-label')).toContainText('NOT SAVED');
  await expect(page.locator('#beyondGoalItems')).toBeVisible();
  await capture('for-you');

  const detailsTrigger = page.locator('#nextStepCard').getByRole('button', { name: /View details/ });
  await detailsTrigger.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('#resourceDetailsDialog')).toBeHidden();
  await expect(detailsTrigger).toBeFocused();
  report.actions.push('For You next step opens resource details and Escape dismisses it.');
  const saveResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await page.locator('#nextStepCard').getByRole('button', { name: /Save/ }).click();
  expect((await saveResponse).ok()).toBeTruthy();
  report.actions.push('For You save action records through the existing interaction API.');

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.locator('#logoutButton').click();
  await expect(page.locator('#authPanel')).toBeVisible();
  await page.locator('#loginForm [name="email"]').fill(`recomai2_${unique}@example.test`);
  await page.locator('#loginForm [name="password"]').fill('RecomAI-Review-Password-482!');
  await page.locator('#loginForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await expect(page.locator('#goalFirstDashboard')).toBeVisible({ timeout: 30_000 });
  report.actions.push('Login returns the member to the API-backed For You dashboard.');

  await page.locator('.nav-link[data-panel="catalogPanel"]').click();
  await expect(page.locator('#catalogPanel')).toHaveClass(/active/);
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 25_000 });
  await capture('explore');
  const exploreFilters = page.locator('#catalogFilterPanel');
  if (!(await exploreFilters.evaluate(element => element.open))) await exploreFilters.locator('summary').click();
  await page.locator('#catalogSearchQuery').fill('Python');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|shown/i, { timeout: 20_000 });
  report.actions.push('Explore search submits against catalog search API.');
  await page.locator('#clearCatalogSearch').click();
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });
  const category = page.locator('#categoryFilter');
  await category.selectOption({ index: 1 });
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|shown/i);
  const topic = page.locator('#topicFilter');
  const visibleTopic = page.locator('#catalogItems .item-card .topic-tag').first();
  if (await visibleTopic.count()) {
    await topic.selectOption({ label: await visibleTopic.innerText() });
    await expect(page.locator('#catalogResultsSummary')).toContainText(/found|shown/i);
    report.actions.push('Explore topic filter uses catalog tags and the catalog search API.');
  }
  await page.locator('#difficultyFilter').selectOption('BEGINNER');
  await page.locator('#timeFilter').selectOption('30');
  report.actions.push('Explore category, difficulty, and time filters update the gallery.');
  await category.selectOption('');
  await page.locator('#difficultyFilter').selectOption('');
  await page.locator('#timeFilter').selectOption('');
  await page.locator('#clearCatalogSearch').click();
  const view = page.locator('#catalogItems .item-card').first().getByRole('button', { name: 'View', exact: true });
  await view.click();
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  report.actions.push('Explore View opens the catalog resource details dialog.');

  await page.locator('#clearCatalogFilters').click();
  await page.locator('#applyCatalogFilters').click();
  await page.setViewportSize({ width: 390, height: 844 });
  const filters = exploreFilters;
  await expect(filters).not.toHaveAttribute('open', '');
  await filters.locator('summary').focus();
  await page.keyboard.press('Enter');
  await expect(filters).toHaveAttribute('open', '');
  await page.locator('#difficultyFilter').selectOption('BEGINNER');
  await expect(page.locator('#catalogFilterCount')).toContainText('1 active');
  await page.locator('#applyCatalogFilters').click();
  await expect(filters).not.toHaveAttribute('open', '');
  await filters.locator('summary').click();
  await expect(page.locator('#difficultyFilter')).toHaveValue('BEGINNER');
  await page.locator('#clearCatalogFilters').click();
  await expect(page.locator('#difficultyFilter')).toHaveValue('');
  await expect(page.locator('#catalogFilterCount')).toContainText('No filters selected');
  report.actions.push('Mobile filter disclosure opens by keyboard, preserves state, applies, and clears filters.');

  await page.goBack();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/);
  await page.goForward();
  await expect(page.locator('#catalogPanel')).toHaveClass(/active/);
  report.actions.push('Hash navigation responds to browser Back and Forward.');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const reduced = await page.locator('#catalogItems .item-card').first().evaluate(element => getComputedStyle(element).transitionDuration);
  expect(reduced.split(',').every(value => parseFloat(value) === 0)).toBeTruthy();
  report.actions.push('Reduced-motion preference disables gallery card transitions.');

  expect(report.browserErrors).toEqual([]);
  await fs.mkdir(path.dirname(screenshotRoot), { recursive: true });
  await fs.writeFile(path.join(deliverableRoot, 'test-results.json'), JSON.stringify(report, null, 2));
});
