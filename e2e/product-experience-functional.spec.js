import { onboardingStage } from './onboarding-helper.js';
import { test, expect } from '@playwright/test';

async function openPanel(page, panelId) {
  if (await page.locator(`#${panelId}`).evaluate(element => element.classList.contains('active'))) return;
  const labels = {
    recommendationsPanel: 'For You', catalogPanel: 'Explore', savedPanel: 'Saved',
    interestProfilePanel: 'My Taste', preferencesPanel: 'Category preferences',
    progressPanel: 'Progress', activityPanel: 'Activity', discoverPanel: 'Profile',
    howWorksPanel: 'How it works'
  };
  const workspaceLink = page.locator('.recommendation-workspace .workspace-nav-link', { hasText: labels[panelId] });
  if (await workspaceLink.isVisible().catch(() => false)) await workspaceLink.click();
  else {
    const link = page.locator(`.nav-link[data-panel="${panelId}"]`);
    if (!(await link.isVisible())) {
      const menu = page.locator('.secondary-navigation');
      if (!(await menu.evaluate(element => element.open))) await menu.locator('summary').click();
    }
    try {
      await link.click({ timeout: 1_500 });
    } catch (error) {
      // The async recommendation response can replace the legacy navigation
      // with the workspace navigation between the visibility check and click.
      // Retry through the visible workspace control only when that transition
      // actually occurred; otherwise preserve the original interaction failure.
      if (!(await workspaceLink.isVisible().catch(() => false))) throw error;
      await workspaceLink.click();
    }
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

test('member journeys keep existing APIs and session behavior working', async ({ page }) => {
  test.setTimeout(180_000);
  const runtimeErrors = [];
  page.on('pageerror', error => runtimeErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) runtimeErrors.push(message.text()); });

  await page.goto('/');
  await expect(page.locator('#demoResourceGrid .demo-resource-card')).toHaveCount(3);
  const sampleDataInterest = page.locator('[data-demo-interest="data"]');
  await sampleDataInterest.focus();
  await sampleDataInterest.press('Space');
  await expect(sampleDataInterest).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('[data-demo-preview-title]')).toHaveText('Ask better questions with SQL');
  await expect(page.locator('[data-demo-preview-art] svg')).toHaveAttribute('aria-label', /Database illustration/);
  await sampleDataInterest.press('Space');
  await expect(sampleDataInterest).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator('[data-demo-preview-title]')).toHaveText('Build your first Java API');
  await page.locator('[data-demo-interest="programming"]').click();
  await page.locator('[data-demo-interest="data"]').click();
  await expect(page.locator('#demoFeedTitle')).toContainText(/data/i);
  await expect(page.locator('#demoResourceGrid')).toContainText('SQL');
  await page.locator('[data-auth="register"]').first().click();
  const id = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const account = { email: `journey_${id}@example.test`, password: 'Journey-Test-Password-482!' };
  await page.locator('#registerForm [name="displayName"]').fill('Journey Review');
  await page.locator('#registerForm [name="username"]').fill(`journey_${id}`);
  await page.locator('#registerForm [name="email"]').fill(account.email);
  await page.locator('#registerForm [name="password"]').fill(account.password);
  await page.locator('#registerForm button[type="submit"]').click();
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await page.locator('#onboardingNiche option').nth(1).waitFor({ state: 'attached' });
  await onboardingStage(page,1); await page.locator('#onboardingNiche').selectOption({ index: 1 });
  await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
  await onboardingStage(page,3); await page.locator('#onboardingForm button[type="submit"]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await waitForRecommendationView(page);

  await expect(page.locator('#nextStepCard h3')).toBeVisible();
  await expect(page.locator('#nextStepCard')).toContainText('Why this was recommended');
  const awaitTitle = await page.locator('#nextStepCard h3').innerText();
  const recommendations = await (await page.request.get('/api/users/me/recommendations?limit=10')).json();
  expect(recommendations.some(row => row.itemTitle === awaitTitle)).toBeTruthy();
  await page.locator('#nextStepCard').getByRole('button', {name:/View details/}).click();
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--green').trim()), 'legacy green accent token should be orange').toBe('#c98765');
  await openPanel(page, 'catalogPanel');

  await page.locator('#catalogSearchQuery').fill('Python');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
  const categoryFilter = page.locator('#categoryFilter');
  if (await categoryFilter.locator('option').count() > 1) await categoryFilter.selectOption({ index: 1 });
  await page.locator('#clearCatalogSearch').click();
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });

  await categoryFilter.selectOption('');
  await page.locator('#catalogSearchQuery').fill('Google Maps');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  const mapsResource = page.locator('#catalogItems .item-card').filter({ hasText: 'Google Maps Trip Planning' }).first();
  await expect(mapsResource).toBeVisible({ timeout: 20_000 });
  await expect(mapsResource.locator('.item-art')).toHaveClass(/art-travel/);
  await page.locator('#clearCatalogSearch').click();
  await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });

  const card = page.locator('#catalogItems .item-card').first();
  const viewRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'View', exact: true }).click();
  expect((await viewRequest).status()).toBe(201);
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  const saveRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'Save', exact: true }).click();
  expect((await saveRequest).status()).toBe(201);
  await card.locator('.rating-control select').selectOption('5');
  const feedbackRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/feedback');
  await card.locator('.rating-control').getByRole('button', { name: /save rating/i }).click();
  expect((await feedbackRequest).ok()).toBeTruthy();
  const progressRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'In progress', exact: true }).click();
  expect((await progressRequest).status()).toBe(201);
  const completeRequest = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/users/me/interactions');
  await card.getByRole('button', { name: 'Mark complete', exact: true }).click();
  expect((await completeRequest).status()).toBe(201);

  await openPanel(page, 'savedPanel');
  await expect(page.locator('#savedItems .item-card')).toHaveCount(1, { timeout: 20_000 });
  await openPanel(page, 'progressPanel');
  await expect(page.locator('#progressPanel [data-progress-completed]')).toHaveText('1');
  await expect(page.locator('#progressPanel [data-progress-in-progress]')).toHaveText('0');
  await expect(page.locator('#progressPanel [data-progress-week]')).not.toHaveText('0');
  await openPanel(page, 'activityPanel');
  await expect(page.locator('#activityList .activity-day').first()).toBeVisible();
  await openPanel(page, 'interestProfilePanel');
  const explorationSlider = page.locator('#explorationValue');
  await expect(explorationSlider).toBeEnabled();
  await explorationSlider.focus();
  await explorationSlider.press('End');
  await expect(page.locator('#explorationMode')).toHaveText('Adventurous');
  const explorationRequest = page.waitForResponse(response => response.request().method() === 'PUT' && new URL(response.url()).pathname === '/api/users/me/recommendations/exploration');
  await page.locator('#explorationForm button[type="submit"]').click();
  expect((await explorationRequest).ok()).toBeTruthy();
  await openPanel(page, 'preferencesPanel');
  const preference = page.locator('#preferenceList .preference-row').first();
  await preference.locator('input[type="range"]').focus();
  await preference.locator('input[type="range"]').press('End');
  await preference.locator('input[type="range"]').press('ArrowLeft');
  const preferenceRequest = page.waitForResponse(response => response.request().method() === 'PUT' && new URL(response.url()).pathname.startsWith('/api/users/me/preferences/'));
  await preference.getByRole('button', { name: 'Save', exact: true }).click();
  expect((await preferenceRequest).ok()).toBeTruthy();
  await openPanel(page, 'howWorksPanel');
  await expect(page.locator('#howWorksPanel .recommendation-steps li')).toHaveCount(4);
  await page.locator('#howWorksPanel .technical-details > summary').click();
  await expect(page.locator('#howWorksPanel [data-signal-weights]')).toBeVisible();
  await page.locator('#returnHome').click();
  await expect(page.locator('#landingPage')).toBeVisible();
  await expect(page.locator('[data-enter-workspace]')).toBeVisible();
  await expect(page.locator('#authPanel')).toBeHidden();
  await page.locator('[data-enter-workspace]').click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/);
  await page.locator('#returnHome').click();
  await expect(page.locator('#landingPage')).toBeVisible();
  await page.locator('[data-enter-workspace]').click();
  await page.goBack();
  await expect(page.locator('#landingPage')).toBeVisible();
  await page.goForward();
  await expect(page.locator('#appShell')).toBeVisible();

  await openPanel(page, 'catalogPanel');
  await page.locator('#logoutButton').click();
  await expect(page.locator('#authPanel')).toBeVisible();
  await page.locator('#loginForm [name="email"]').fill(account.email);
  await page.locator('#loginForm [name="password"]').fill(account.password);
  await page.locator('#loginForm button[type="submit"]').click();
  await expect(page.locator('#catalogPanel')).toHaveClass(/active/, { timeout: 30_000 });
  await expect(page.locator('#appShell')).toBeVisible();
  expect(runtimeErrors).toEqual([]);
});
