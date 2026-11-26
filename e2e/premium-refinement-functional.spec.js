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
    await link.click();
  }
  await expect(page.locator(`#${panelId}`)).toHaveClass(/active/);
}

test('member journeys keep existing APIs and session behavior working', async ({ page }) => {
  test.setTimeout(180_000);
  const runtimeErrors = [];
  page.on('pageerror', error => runtimeErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) runtimeErrors.push(message.text()); });

  await page.goto('/');
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

  await expect(page.locator('#goalFirstDashboard')).toBeVisible();
  await expect(page.locator('#nextStepCard h3')).toBeVisible();
  await expect(page.locator('#nextStepCard')).toContainText('Why this was recommended');
  await page.locator('#nextStepCard').getByRole('button', {name:/View details/}).click();
  await expect(page.locator('#resourceDetailsDialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await openPanel(page, 'catalogPanel');

  await page.locator('#catalogSearchQuery').fill('Python');
  await page.locator('#catalogSearchForm button[type="submit"]').click();
  await expect(page.locator('#catalogResultsSummary')).toContainText(/found|showing|result/i, { timeout: 20_000 });
  const categoryFilter = page.locator('#categoryFilter');
  if (await categoryFilter.locator('option').count() > 1) await categoryFilter.selectOption({ index: 1 });
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

  await openPanel(page, 'savedPanel');
  await expect(page.locator('#savedItems .item-card')).toHaveCount(1, { timeout: 20_000 });
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
