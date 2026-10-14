import { test, expect } from '@playwright/test';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

async function waitForResetLink(mailpitUrl, email, excludedIds = []) {
  let found = null;
  await expect.poll(async () => {
    const inbox = await fetch(`${mailpitUrl}/api/v1/messages`);
    if (!inbox.ok) return false;
    const listing = await inbox.json();
    const message = (listing.messages || []).find(entry => !excludedIds.includes(entry.ID)
      && (entry.To || []).some(to => to.Address === email));
    if (!message) return false;
    const detailResponse = await fetch(`${mailpitUrl}/api/v1/message/${message.ID}`);
    if (!detailResponse.ok) return false;
    const detail = await detailResponse.json();
    const body = `${detail.Text || ''}\n${detail.HTML || ''}`;
    const link = body.match(/https?:\/\/[^\s"<>]+#reset-password\?token=[A-Za-z0-9_-]{43}/)?.[0];
    if (!link) return false;
    found = { link, id: message.ID };
    return true;
  }, { timeout: 20_000 }).toBeTruthy();
  return found;
}

function expireIsolatedResetToken(token) {
  const tokenHash = createHash('sha256').update(token, 'ascii').digest('hex');
  const sql = `UPDATE password_reset_tokens SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE token_hash=UNHEX('${tokenHash}'); SELECT ROW_COUNT();\n`;
  const result = spawnSync('docker', [
    'compose', '-p', process.env.E2E_COMPOSE_PROJECT, 'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
  ], { input: sql, encoding: 'utf8' });
  if (result.error || result.status !== 0 || !result.stdout.trim().endsWith('1')) {
    throw new Error('Could not prepare the isolated expired-token verification case.');
  }
}

test('search, account, password recovery, rating, session and recommendation journey', async ({ page, browser }) => {
  test.setTimeout(90_000);
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const account = {
    displayName: 'RecomAI browser test',
    username: `e2e_${suffix}`,
    email: `e2e_${suffix}@example.test`,
    password: 'Test-only-Pass-482!'
  };
  const pageErrors = [];
  const consoleErrors = [];
  const serverErrors = [];
  let deletionConfirmationSeen = false;
  let expectedStartupServerFailure = false;
  let expectedSignupServerFailure = false;
  let recoveryContext;
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('response', response => {
    if (response.status() >= 500) {
      const request = response.request();
      if (expectedStartupServerFailure && new URL(response.url()).pathname === '/api/users/me') {
        expectedStartupServerFailure = false;
        return;
      }
      if (expectedSignupServerFailure && new URL(response.url()).pathname === '/api/auth/register') {
        expectedSignupServerFailure = false;
        return;
      }
      serverErrors.push(`${request.method()} ${new URL(response.url()).pathname} ${response.status()}`);
    }
  });
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      consoleErrors.push(message.text());
    }
  });
  page.on('dialog', async dialog => {
    if (dialog.type() === 'prompt') {
      deletionConfirmationSeen ||= dialog.message().includes('permanently deletes your account');
      await dialog.accept('DELETE');
    }
    else await dialog.accept();
  });

  try {
    let sessionCheckCount = 0;
    await page.route('**/api/users/me', async route => {
      sessionCheckCount += 1;
      if (sessionCheckCount === 1) {
        await route.abort();
      } else if (sessionCheckCount === 2) {
        expectedStartupServerFailure = true;
        await route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"Temporary server error"}' });
      } else {
        await route.continue();
      }
    });
    await page.goto('/');
    await expect(page.locator('#heroTitle')).toBeVisible();
    await expect(page.locator('#connectionStatus')).toBeVisible();
    await expect(page.locator('#connectionStatusMessage')).toContainText('account state is unknown');
    await expect(page.locator('#connectionStatusMessage')).toContainText("couldn't reach RecomAI");
    await page.locator('#retrySessionCheck').click();
    await expect(page.locator('#connectionStatusMessage')).toContainText('server returned an error');
    await expect(page.locator('#connectionStatusMessage')).toContainText('account state is unknown');
    await page.locator('#retrySessionCheck').click();
    await expect(page.locator('#connectionStatus')).toBeHidden();
    await page.unroute('**/api/users/me');

    await page.locator('#discoveryQuery').fill('   ');
    await page.getByRole('button', { name: 'Explore recommendations' }).click();
    await expect(page.locator('#discoveryMessage')).toContainText('Enter a topic or keyword');
    await expect(page.locator('#authPanel')).toBeHidden();
    await page.locator('#discoveryQuery').evaluate(input => {
      input.value = 'x'.repeat(121);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.locator('#discoveryForm').evaluate(form => form.requestSubmit());
    await expect(page.locator('#discoveryMessage')).toContainText('120 characters');

    await page.locator('#discoveryQuery').fill('cloud computing courses');
    await page.getByRole('button', { name: 'Explore recommendations' }).click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await expect(page.locator('#authMessage')).toContainText('cloud computing courses');

    await page.locator('#showRegister').focus();
    await page.locator('#showRegister').press('ArrowLeft');
    await expect(page.locator('#showLogin')).toHaveAttribute('aria-selected', 'true');
    await page.locator('#showLogin').press('End');
    await expect(page.locator('#showRegister')).toHaveAttribute('aria-selected', 'true');
    await page.locator('#showRegister').click();
    await expect(page.locator('#authMessage')).toBeEmpty();
    await expect(page.locator('#loginForm')).toBeHidden();
    await expect(page.locator('#registerForm')).toBeVisible();
    await page.locator('#registerForm [name=displayName]').fill(account.displayName);
    await page.locator('#registerForm [name=username]').fill(account.username);
    await page.locator('#registerForm [name=email]').fill(account.email);
    await page.locator('#registerForm [name=password]').fill(account.password);
    let delayFirstRegistration = true;
    await page.route('**/api/auth/register', async route => {
      if (delayFirstRegistration && route.request().method() === 'POST') {
        delayFirstRegistration = false;
        await new Promise(resolve => setTimeout(resolve, 300));
      }
      await route.continue();
    });
    const registrationResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/auth/register') && response.request().method() === 'POST');
    await page.getByRole('button', { name: /Create your account/ }).click();
    await expect(page.locator('#showLogin')).toBeDisabled();
    await expect(page.locator('#registerForm [name=password]')).toBeDisabled();
    await expect(page.locator('#registerForm button[type=submit]')).toBeDisabled();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });
    const registrationResponse = await registrationResponsePromise;
    expect(registrationResponse.status()).toBe(201);
    expect((await registrationResponse.json()).username).toBe(account.username);
    expect(registrationResponse.request().postDataJSON()).toMatchObject({
      displayName: account.displayName,
      username: account.username,
      email: account.email
    });
    expect(typeof registrationResponse.request().postDataJSON().password).toBe('string');
    await page.unroute('**/api/auth/register');
    expect(new URL(page.url()).searchParams.has('password')).toBeFalsy();
    await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 20_000 });
    const niche = page.locator('#onboardingNiche');
    await expect.poll(async () => niche.locator('option').count()).toBeGreaterThan(1);
    await niche.selectOption({ index: 1 });
    await page.locator('#onboardingPurpose').selectOption('LEARN');
    const topic = page.locator('#onboardingTopics input').first();
    if (await topic.count()) await topic.check();
    await page.getByRole('button', { name: /Build my Explore feed/ }).click();
    await expect(page.locator('#dashboardMessage')).toContainText('Your new picks are ready.');
    const themeToggle = page.locator('#themeToggle');
    await expect(themeToggle).toBeVisible();
    await themeToggle.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect.poll(() => page.evaluate(() => localStorage.getItem('recomai-theme'))).toBe('light');
    await expect(page.locator('#catalogSearchQuery')).toHaveValue('cloud computing courses', { timeout: 20_000 });
    await expect(page.locator('#catalogItems')).toBeVisible();
    await page.reload();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await page.locator('#themeToggle').click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    // Recovery uses a separate unauthenticated browser context; the original session
    // remains active so the reset can prove server-side revocation.
    recoveryContext = await browser.newContext();
    const recoveryPage = await recoveryContext.newPage();
    await recoveryPage.goto('/');
    await recoveryPage.locator('[data-auth="login"]').first().click();
    await recoveryPage.locator('#forgotPasswordLink').click();
    await expect(recoveryPage.locator('#forgotPasswordForm')).toBeVisible();
    await recoveryPage.locator('#forgotPasswordForm [name=email]').fill(account.email);
    const resetRequestResponsePromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/request') && response.request().method() === 'POST');
    await recoveryPage.getByRole('button', { name: /Send reset instructions/ }).click();
    const resetRequestResponse = await resetRequestResponsePromise;
    expect(resetRequestResponse.status()).toBe(202);
    const genericResetMessage = (await resetRequestResponse.json()).message;
    expect(genericResetMessage).toBe('If an account exists for this email address, password reset instructions will be sent.');
    await expect(recoveryPage.locator('#authMessage')).toHaveText(genericResetMessage);

    await recoveryPage.locator('#forgotPasswordForm [name=email]').fill(`unknown_${suffix}@example.test`);
    const unknownResetResponsePromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/request') && response.request().method() === 'POST');
    await recoveryPage.getByRole('button', { name: /Send reset instructions/ }).click();
    const unknownResetResponse = await unknownResetResponsePromise;
    expect(unknownResetResponse.status()).toBe(202);
    expect((await unknownResetResponse.json()).message).toBe(genericResetMessage);

    const mailpitUrl = process.env.E2E_MAILPIT_URL;
    expect(mailpitUrl).toBeTruthy();
    const firstMail = await waitForResetLink(mailpitUrl, account.email);
    const firstToken = new URLSearchParams(new URL(firstMail.link).hash.split('?')[1]).get('token');

    await recoveryPage.locator('#forgotPasswordForm [name=email]').fill(account.email);
    const replacementRequestPromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/request') && response.request().method() === 'POST');
    await recoveryPage.getByRole('button', { name: /Send reset instructions/ }).click();
    expect((await replacementRequestPromise).status()).toBe(202);
    const replacementMail = await waitForResetLink(mailpitUrl, account.email, [firstMail.id]);
    const replacementToken = new URLSearchParams(new URL(replacementMail.link).hash.split('?')[1]).get('token');

    const newPassword = 'Reset-only-Pass-583!';
    await recoveryPage.goto(firstMail.link);
    await expect.poll(() => new URL(recoveryPage.url()).hash).toBe('#home');
    await recoveryPage.locator('#resetPasswordForm [name=password]').fill(newPassword);
    await recoveryPage.locator('#resetPasswordForm [name=confirmPassword]').fill(newPassword);
    const invalidatedResponsePromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/complete') && response.request().method() === 'POST');
    await recoveryPage.locator('#resetPasswordForm button[type=submit]').click();
    expect((await invalidatedResponsePromise).status()).toBe(400);
    await expect(recoveryPage.locator('#authMessage')).toContainText('invalid or expired');

    expireIsolatedResetToken(replacementToken);
    await recoveryPage.goto(replacementMail.link);
    await expect.poll(() => new URL(recoveryPage.url()).hash).toBe('#home');
    await recoveryPage.locator('#resetPasswordForm [name=password]').fill(newPassword);
    await recoveryPage.locator('#resetPasswordForm [name=confirmPassword]').fill(newPassword);
    const expiredResponsePromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/complete') && response.request().method() === 'POST');
    await recoveryPage.locator('#resetPasswordForm button[type=submit]').click();
    expect((await expiredResponsePromise).status()).toBe(400);
    await expect(recoveryPage.locator('#authMessage')).toContainText('invalid or expired');

    await recoveryPage.locator('#requestAnotherReset').click();
    await recoveryPage.locator('#forgotPasswordForm [name=email]').fill(account.email);
    const finalResetRequestPromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/request') && response.request().method() === 'POST');
    await recoveryPage.getByRole('button', { name: /Send reset instructions/ }).click();
    expect((await finalResetRequestPromise).status()).toBe(202);
    const finalMail = await waitForResetLink(mailpitUrl, account.email, [firstMail.id, replacementMail.id]);
    const resetLink = finalMail.link;

    await recoveryPage.goto(resetLink);
    await expect.poll(() => new URL(recoveryPage.url()).hash).toBe('#home');
    await expect(recoveryPage.locator('#resetPasswordForm')).toBeVisible();
    await recoveryPage.locator('#resetPasswordForm [name=password]').fill(newPassword);
    await recoveryPage.locator('#resetPasswordForm [name=confirmPassword]').fill('Different-password-930!');
    await recoveryPage.locator('#resetPasswordForm button[type=submit]').click();
    await expect(recoveryPage.locator('#authMessage')).toContainText('passwords do not match');
    await recoveryPage.locator('#resetPasswordForm [name=password]').fill(newPassword);
    await recoveryPage.locator('#resetPasswordForm [name=confirmPassword]').fill(newPassword);
    const resetCompleteResponsePromise = recoveryPage.waitForResponse(response =>
      response.url().endsWith('/api/auth/password-reset/complete') && response.request().method() === 'POST');
    await recoveryPage.getByRole('button', { name: /Reset password/ }).click();
    const resetCompleteResponse = await resetCompleteResponsePromise;
    expect(resetCompleteResponse.status()).toBe(200);
    await expect(recoveryPage.locator('#authMessage')).toContainText('password has been reset');

    const revokedSessionStatus = await page.evaluate(async () => (await fetch('/api/users/me')).status);
    expect(revokedSessionStatus).toBe(401);
    await recoveryPage.locator('#loginForm [name=email]').fill(account.email);
    await recoveryPage.locator('#loginForm [name=password]').fill(account.password);
    await recoveryPage.locator('#loginForm button[type=submit]').click();
    await expect(recoveryPage.locator('#authMessage')).toContainText('Email or password is incorrect.');
    await recoveryPage.locator('#loginForm [name=password]').fill(newPassword);
    await recoveryPage.locator('#loginForm button[type=submit]').click();
    await expect(recoveryPage.locator('#appShell')).toBeVisible({ timeout: 20_000 });

    await recoveryPage.goto(resetLink);
    await expect(recoveryPage.locator('#resetPasswordForm')).toBeVisible();
    await recoveryPage.locator('#resetPasswordForm [name=password]').fill('Replay-attempt-593!');
    await recoveryPage.locator('#resetPasswordForm [name=confirmPassword]').fill('Replay-attempt-593!');
    await recoveryPage.locator('#resetPasswordForm button[type=submit]').click();
    await expect(recoveryPage.locator('#authMessage')).toContainText('invalid or expired');

    const rateLimitStatuses = await recoveryPage.evaluate(async () => {
      const statuses = [];
      for (let index = 0; index < 3; index += 1) {
        const response = await fetch('/api/auth/password-reset/request', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: `rate-limit-${index}@example.test` })
        });
        statuses.push(response.status);
      }
      return statuses;
    });
    expect(rateLimitStatuses).toEqual([202, 202, 429]);

    account.password = newPassword;
    await page.reload();
    await expect(page.locator('#authPanel')).toBeHidden();
    await expect(page.locator('#landingPage')).toBeVisible();
    await page.locator('[data-auth="login"]').first().click();
    await page.locator('#loginForm [name=email]').fill(account.email);
    await page.locator('#loginForm [name=password]').fill(account.password);
    await page.locator('#loginForm button[type=submit]').click();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });

    await page.route('**/api/auth/logout', route => route.abort());
    await page.locator('#logoutButton').click();
    await expect(page.locator('#appShell')).toBeVisible();
    await expect(page.locator('#dashboardMessage')).toContainText('session status is unknown');
    await page.unroute('**/api/auth/logout');
    await page.locator('#logoutButton').click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await expect(page.locator('#authMessage')).toContainText('You are signed out.');

    await page.locator('#showRegister').click();
    await page.locator('#registerForm [name=displayName]').fill(account.displayName);
    await page.locator('#registerForm [name=username]').fill(account.username + '_again');
    await page.locator('#registerForm [name=email]').fill(account.email);
    await page.locator('#registerForm [name=password]').fill(account.password);
    const duplicateEmailResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/auth/register') && response.request().method() === 'POST');
    await page.getByRole('button', { name: /Create your account/ }).click();
    const duplicateEmailResponse = await duplicateEmailResponsePromise;
    expect(duplicateEmailResponse.status()).toBe(409);
    await expect(page.locator('#authMessage')).toContainText('already in use');
    await expect(page.locator('#duplicateRegistrationAction')).toBeVisible();
    await expect(page.locator('#showRegister')).toHaveAttribute('aria-selected', 'true');
    await page.locator('#duplicateRegistrationAction').click();
    await expect(page.locator('#showLogin')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#loginForm [name=email]')).toHaveValue(account.email);

    await page.locator('#showRegister').click();
    await page.locator('#registerForm [name=displayName]').fill(account.displayName);
    await page.locator('#registerForm [name=username]').fill(account.username);
    await page.locator('#registerForm [name=email]').fill(`different_${suffix}@example.test`);
    await page.locator('#registerForm [name=password]').fill(account.password);
    let usernameDuplicateResponse;
    const usernameDuplicatePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/auth/register') && response.request().method() === 'POST');
    await page.getByRole('button', { name: /Create your account/ }).click();
    usernameDuplicateResponse = await usernameDuplicatePromise;
    expect(usernameDuplicateResponse.status()).toBe(409);
    await expect(page.locator('#authMessage')).toContainText('already in use');

    await page.locator('#showRegister').click();
    await page.locator('#registerForm [name=username]').fill('bad username');
    await page.locator('#registerForm [name=email]').fill(`invalid_${suffix}@example.test`);
    await page.locator('#registerForm [name=password]').fill(account.password);
    let invalidRequestSent = false;
    const invalidRequestListener = response => {
      if (response.url().endsWith('/api/auth/register') && response.request().method() === 'POST') invalidRequestSent = true;
    };
    page.on('response', invalidRequestListener);
    await page.getByRole('button', { name: /Create your account/ }).click();
    expect(await page.locator('#registerForm [name=username]').evaluate(input => input.validity.valid)).toBeFalsy();
    expect(invalidRequestSent).toBeFalsy();
    page.off('response', invalidRequestListener);

    await page.locator('#registerForm [name=username]').fill(account.username + '_server');
    await page.locator('#registerForm [name=email]').fill(`server_${suffix}@example.test`);
    await page.route('**/api/auth/register', route => {
      expectedSignupServerFailure = true;
      return route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"The request could not be completed."}' });
    });
    await page.getByRole('button', { name: /Create your account/ }).click();
    await expect(page.locator('#authMessage')).toContainText('request could not be completed');
    await expect(page.locator('#authMessage')).not.toContainText('already in use');
    await page.unroute('**/api/auth/register');

    await page.route('**/api/auth/register', route => route.abort());
    await page.getByRole('button', { name: /Create your account/ }).click();
    await expect(page.locator('#authMessage')).toContainText("couldn't confirm your registration");
    await expect(page.locator('#authMessage')).not.toContainText('already in use');
    await expect(page.locator('#duplicateRegistrationAction')).toBeHidden();
    await page.unroute('**/api/auth/register');

    await page.route('**/api/auth/register', route => route.fulfill({ status: 409, contentType: 'application/json', body: '{"message":"The request conflicts with existing data."}' }));
    await page.getByRole('button', { name: /Create your account/ }).click();
    await expect(page.locator('#authMessage')).toContainText('request conflicts with existing data');
    await expect(page.locator('#authMessage')).not.toContainText('already in use');
    await expect(page.locator('#duplicateRegistrationAction')).toBeHidden();
    await page.unroute('**/api/auth/register');

    await page.locator('#showLogin').click();

    await page.locator('#loginForm [name=email]').fill(account.email);
    await page.locator('#loginForm [name=password]').fill('Wrong-password-481!');
    await page.locator('#loginForm button[type=submit]').click();
    await expect(page.locator('#authMessage')).toContainText('Email or password is incorrect.');

    await page.locator('#loginForm [name=password]').fill(account.password);
    await page.locator('#loginForm button[type=submit]').click();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });
    expect(new URL(page.url()).searchParams.has('password')).toBeFalsy();
    await expect(page.locator('#onboardingPanel')).not.toHaveClass(/active/);
    await page.reload();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });

    await page.locator('.nav-link[data-panel="catalogPanel"]').click();
    await page.locator('#catalogSearchQuery').fill('cloud computing courses');
    const searchTracking = page.waitForResponse(response => response.url().includes('/api/users/me/interactions/search'));
    await page.locator('#catalogSearchForm button[type=submit]').click();
    expect((await searchTracking).ok()).toBeTruthy();
    await expect(page.locator('#catalogResultsSummary')).toContainText(/resources? found/);
    const recordedSearches = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/interactions?limit=100');
      return response.ok ? response.json() : [];
    });
    expect(recordedSearches.some(entry => entry.type === 'SEARCH' && entry.searchQuery === 'cloud computing courses')).toBeTruthy();
    await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });
    await page.locator('#clearCatalogSearch').click();
    await expect(page.locator('#catalogPagination')).toBeVisible();
    await page.locator('#catalogNextPage').click();
    await expect(page.locator('#catalogPageSummary')).toContainText('Page 2');
    await page.locator('#catalogPreviousPage').click();
    await expect(page.locator('#catalogPageSummary')).toContainText('Page 1');
    await page.locator('#catalogSearchQuery').fill('cloud computing courses');
    await page.locator('#catalogSearchForm button[type=submit]').click();
    await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({ timeout: 20_000 });
    const rating = page.locator('#catalogItems .rating-control').first();
    await expect(rating.locator('select')).toBeEnabled({ timeout: 20_000 });
    await rating.locator('select').selectOption('5');
    await rating.getByRole('button', { name: /Save rating/ }).click();
    await expect(rating.locator('.rating-status')).toContainText('Rating saved');
    const firstCatalogCard = page.locator('#catalogItems .item-card').first();
    await firstCatalogCard.getByRole('button', { name: 'In progress' }).click();
    await firstCatalogCard.getByRole('button', { name: 'Mark complete' }).click();
    await page.locator('.nav-link[data-panel="progressPanel"]').click();
    await expect(page.locator('#progressPanel')).toHaveClass(/active/);
    await expect(page.locator('#progressPanel [data-completed] .item-card')).toHaveCount(1);
    await expect(page.locator('#progressPanel [data-week-chart] .weekly-bar')).toHaveCount(7);
    const progressState = await page.evaluate(async () => {
      const historyResponse = await fetch('/api/users/me/interactions?limit=100');
      const history = await historyResponse.json();
      const candidate = history.find(entry => entry.type === 'COMPLETE');
      return candidate ? Number(candidate.itemId) : null;
    });
    expect(progressState).toBeTruthy();
    const progressTransitions = await page.evaluate(async itemId => {
      const record = (type, requestId) => fetch('/api/users/me/interactions', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId, type, requestId })
      });
      const ids = Array.from({ length: 105 }, (_, index) =>
        `11111111-1111-4111-8111-${String(index).padStart(12, '0')}`);
      const repeatedId = '55555555-5555-4555-8555-555555555555';
      const replayA = await record('LIKE', repeatedId);
      const replayB = await record('LIKE', repeatedId);
      const distinct = await record('LIKE', '66666666-6666-4666-8666-666666666666');
      const replayAData = await replayA.json();
      const replayBData = await replayB.json();
      const distinctData = await distinct.json();
      await Promise.all(ids.map(id => record('VIEW', id)));
      await record('COMPLETE', '22222222-2222-4222-8222-222222222222');
      await Promise.all(ids.map((_, index) => record('VIEW',
        `33333333-3333-4333-8333-${String(index).padStart(12, '0')}`)));
      const afterOldCompletion = await (await fetch('/api/users/me/interactions/progress')).json();
      const firstRenewed = await record('READ_PROGRESS', '44444444-4444-4444-8444-444444444444').then(response => response.json());
      const latestRenewed = await record('READ_PROGRESS', '77777777-7777-4777-8777-777777777777').then(response => response.json());
      const afterRenewedProgress = await (await fetch('/api/users/me/interactions/progress')).json();
      const currentItemState = afterRenewedProgress.find(entry => Number(entry.itemId) === itemId);
      return {
        replayReturnsSameInteraction: replayAData.interactionId === replayBData.interactionId,
        distinctActionCreatesNewInteraction: distinctData.interactionId !== replayAData.interactionId,
        completionSurvivesBeyondHistoryWindow: afterOldCompletion.some(entry =>
          Number(entry.itemId) === itemId && entry.type === 'COMPLETE'),
        renewedProgressWins: currentItemState?.type === 'READ_PROGRESS',
        multipleProgressUpdatesChooseLatest: currentItemState?.interactionId === latestRenewed.interactionId
          && latestRenewed.interactionId !== firstRenewed.interactionId
      };
    }, progressState);
    expect(progressTransitions.replayReturnsSameInteraction).toBeTruthy();
    expect(progressTransitions.distinctActionCreatesNewInteraction).toBeTruthy();
    expect(progressTransitions.completionSurvivesBeyondHistoryWindow).toBeTruthy();
    expect(progressTransitions.renewedProgressWins).toBeTruthy();
    expect(progressTransitions.multipleProgressUpdatesChooseLatest).toBeTruthy();
    await page.locator('.nav-link[data-panel="catalogPanel"]').click();
    await firstCatalogCard.getByRole('button', { name: 'Save', exact: true }).click();
    await page.locator('.nav-link[data-panel="savedPanel"]').click();
    await page.locator('#collectionName').fill('E2E learning list');
    await page.locator('#collectionForm button[type="submit"]').click();
    await expect(page.locator('#collectionSelect option').filter({ hasText: 'E2E learning list' })).toHaveCount(1);
    const savedCard = page.locator('#savedItems .item-card').first();
    await savedCard.locator('.collection-item-action select').selectOption({ label: 'E2E learning list' });
    await savedCard.getByRole('button', { name: 'Add to collection' }).click();
    await page.locator('#collectionSelect').selectOption({ label: 'E2E learning list' });
    await expect(page.locator('#savedItems .item-card')).toHaveCount(1);
    const exportedData = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/export');
      return { status: response.status, body: response.ok ? await response.json() : null };
    });
    expect(exportedData.status).toBe(200);
    expect(exportedData.body.profile.username).toBe(account.username);
    expect(exportedData.body.profile).not.toHaveProperty('password_hash');
    expect(exportedData.body.collections.some(row => row.name === 'E2E learning list')).toBeTruthy();
    const savedFeedback = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/feedback');
      return response.ok ? response.json() : [];
    });
    expect(savedFeedback.length).toBeGreaterThan(0);
    expect(savedFeedback.some(entry => Number(entry.rating) === 5)).toBeTruthy();
    const otherContext = await browser.newContext();
    try {
      const otherPage = await otherContext.newPage();
      await otherPage.goto(new URL('/', page.url()).toString());
      const otherRegistration = await otherPage.evaluate(async account => {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(account)
        });
        return { status: response.status, body: response.ok ? await response.json() : null };
      }, {
        displayName: 'Privacy test',
        username: `privacy_${suffix}`,
        email: `privacy_${suffix}@example.test`,
        password: 'Test-only-Pass-482!'
      });
      expect(otherRegistration.status).toBe(201);
      const isolatedPrivateData = await otherPage.evaluate(async () => {
        const [history, feedback] = await Promise.all([
          fetch('/api/users/me/interactions?limit=100'),
          fetch('/api/users/me/feedback')
        ]);
        const [historyBody, feedbackBody, progressBody] = await Promise.all([
          history.json(), feedback.json(), fetch('/api/users/me/interactions/progress').then(response => response.json())
        ]);
        return { history: historyBody, feedback: feedbackBody, progress: progressBody };
      });
      expect(isolatedPrivateData.history).toHaveLength(0);
      expect(isolatedPrivateData.feedback).toHaveLength(0);
      expect(isolatedPrivateData.progress).toHaveLength(0);
      // A guaranteed high-enough-ranked item keeps this popularity-threshold check
      // independent of the arbitrary newest-item position in a 200-item catalog.
      const repeatedItemId = await page.evaluate(async () => {
        const response = await fetch('/api/items');
        const items = await response.json();
        const editorPick = items.find(item => item.editorPick);
        return editorPick ? Number(editorPick.itemId) : null;
      });
      expect(repeatedItemId).toBeGreaterThan(0);
      const repeatedLikeStatuses = await page.evaluate(async itemId => Promise.all(
        Array.from({ length: 5 }, () => fetch('/api/users/me/interactions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ itemId, type: 'LIKE' })
        }).then(response => response.status))
      ), repeatedItemId);
      expect(repeatedLikeStatuses.every(status => status === 201)).toBeTruthy();
      const otherRecommendations = await otherPage.evaluate(async () => {
        const response = await fetch('/api/users/me/recommendations?limit=50', { method: 'POST' });
        return response.ok ? response.json() : [];
      });
      const repeatedItemRecommendation = otherRecommendations.find(entry => Number(entry.itemId) === repeatedItemId);
      expect(repeatedItemRecommendation).toBeTruthy();
      expect(repeatedItemRecommendation.reasonType).not.toBe('POPULARITY');
      expect(Number(repeatedItemRecommendation.scoreBreakdown.catalog_popularity)).toBe(0);
    } finally {
      await otherContext.close();
    }

    await page.locator('.nav-link[data-panel="recommendationsPanel"]').click();
    await page.locator('#generateRecommendations').click();
    await expect(page.locator('#recommendationList .recommendation-card').first()).toBeVisible({ timeout: 20_000 });
    const preferenceRanking = await page.evaluate(async () => {
      const categoriesResponse = await fetch('/api/categories');
      const categories = await categoriesResponse.json();
      await Promise.all(categories.map(category => fetch(`/api/users/me/preferences/${category.categoryId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ weight: 0 })
      })));
      const baselineResponse = await fetch('/api/users/me/recommendations?limit=10', { method: 'POST' });
      const baseline = await baselineResponse.json();
      const categoryId = categories[0].categoryId;
      const preferenceResponse = await fetch(`/api/users/me/preferences/${categoryId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ weight: 1 })
      });
      const updatedResponse = await fetch('/api/users/me/recommendations?limit=10', { method: 'POST' });
      const updated = await updatedResponse.json();
      return { categoryId, baseline, updated, preferenceStatus: preferenceResponse.status };
    });
    expect(preferenceRanking.preferenceStatus).toBe(204);
    expect(preferenceRanking.baseline.every(entry => Number(entry.scoreBreakdown?.category_preference || 0) === 0)).toBeTruthy();
    expect(preferenceRanking.updated.some(entry => Number(entry.scoreBreakdown?.category_preference || 0) > 0)).toBeTruthy();
    await page.locator('#generateRecommendations').click();
    await expect(page.locator('#generateRecommendations')).toHaveText(/Find my picks/i);
    await expect(page.locator('#recommendationList .recommendation-card').first()).toBeVisible({ timeout: 20_000 });
    const storedRecommendations = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/recommendations?limit=10');
      return response.ok ? response.json() : [];
    });
    expect(storedRecommendations.every(entry => {
      const sum = Object.values(entry.scoreBreakdown || {}).reduce((total, points) => total + Number(points), 0);
      return Math.abs(sum - Number(entry.score)) < 0.0001
        && !/probability|chance of liking/i.test(entry.reason || '');
    })).toBeTruthy();
    const hasColdStartPick = storedRecommendations.some(entry => entry.reasonType === 'COLD_START');
    if (hasColdStartPick) {
      await expect(page.locator('#recommendationEvidenceNote')).toBeVisible();
      await expect(page.locator('#recommendationEvidenceNote')).toContainText('exploratory');
    } else {
      await expect(page.locator('#recommendationEvidenceNote')).toBeHidden();
    }
    const recentlyViewedItemId = Number(storedRecommendations[0].itemId);
    const viewSuppression = await page.evaluate(async itemId => {
      const tracked = await fetch('/api/users/me/interactions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId, type: 'VIEW' })
      });
      const response = await fetch('/api/users/me/recommendations?limit=50', { method: 'POST' });
      const updated = response.ok ? await response.json() : [];
      return { trackStatus: tracked.status, stillRecommended: updated.some(entry => Number(entry.itemId) === itemId) };
    }, recentlyViewedItemId);
    expect(viewSuppression.trackStatus).toBe(201);
    expect(viewSuppression.stillRecommended).toBeFalsy();
    await page.locator('#generateRecommendations').click();
    await expect(page.locator('#generateRecommendations')).toHaveText(/Find my picks/i);
    await expect(page.locator('#recommendationList .recommendation-card').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('#generateRecommendations')).toBeVisible();
    const firstRecommendation = page.locator('#recommendationList .recommendation-card').first();
    await expect(firstRecommendation.locator('.recommendation-score')).toHaveCount(0);
    await expect(page.locator('.recommendation-intro')).toContainText(/refresh your picks/i);
    await expect(firstRecommendation.locator('.recommendation-actions button')).toHaveCount(5);
    await firstRecommendation.locator('details.recommendation-explanation summary').click();
    await expect(firstRecommendation.locator('.recommendation-score-note'))
      .toContainText('not a probability');
    await expect(firstRecommendation.locator('.recommendation-signals'))
      .not.toContainText('_');
    await page.setViewportSize({ width: 1440, height: 900 });
    await firstRecommendation.screenshot({ path: 'test-results/recommendation-desktop.png' });
    await page.setViewportSize({ width: 390, height: 844 });
    await firstRecommendation.scrollIntoViewIfNeeded();
    const mobileLayout = await page.evaluate(() => ({
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth
    }));
    expect(mobileLayout.documentWidth).toBeLessThanOrEqual(mobileLayout.viewportWidth);
    await expect(firstRecommendation.locator('.recommendation-actions button')).toHaveCount(5);
    const mobileCardGeometry = await firstRecommendation.evaluate(card => {
      const title = card.querySelector('h3').getBoundingClientRect();
      const actions = card.querySelector('.recommendation-actions').getBoundingClientRect();
      return { titleTop: title.top, titleBottom: title.bottom, actionsTop: actions.top };
    });
    expect(mobileCardGeometry.actionsTop).toBeGreaterThanOrEqual(mobileCardGeometry.titleBottom);
    await firstRecommendation.screenshot({ path: 'test-results/recommendation-mobile.png' });
    const likedResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/users/me/interactions' && response.request().method() === 'POST');
    await firstRecommendation.getByRole('button', { name: /^Like / }).click();
    expect((await likedResponse).status()).toBe(201);
    const likes = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/interactions?limit=100');
      return response.ok ? response.json() : [];
    });
    expect(likes.some(entry => entry.type === 'LIKE')).toBeTruthy();
    const hiddenCard = page.locator('#recommendationList .recommendation-card').nth(1);
    const hiddenTitle = await hiddenCard.locator('h3').first().textContent();
    await hiddenCard.getByRole('button', { name: /^Hide / }).click();
    await expect(page.locator('#recommendationList .recommendation-card h3').filter({ hasText: hiddenTitle || '' }))
      .toHaveCount(0);
    const rejectedTitle = await firstRecommendation.locator('h3').first().textContent();
    await firstRecommendation.locator('button.recommendation-dismiss').click();
    await expect(page.locator('#recommendationList .recommendation-card h3').filter({ hasText: rejectedTitle || '' }))
      .toHaveCount(0);
    await page.locator('#generateRecommendations').click();
    await expect(page.locator('#recommendationList .recommendation-card').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('#recommendationList .recommendation-card h3').filter({ hasText: rejectedTitle || '' }))
      .toHaveCount(0);
    await expect(page.locator('#recommendationList .recommendation-card h3').filter({ hasText: hiddenTitle || '' }))
      .toHaveCount(0);

    await page.locator('.nav-link[data-panel="activityPanel"]').click();
    await page.locator('#clearHistoryButton').click();
    await expect(page.locator('#dashboardMessage')).toContainText('history cleared');
    const clearedData = await page.evaluate(async () => {
      const [history, recommendations, feedback] = await Promise.all([
        fetch('/api/users/me/interactions?limit=100'),
        fetch('/api/users/me/recommendations?limit=10'),
        fetch('/api/users/me/feedback')
      ]);
      return { history: await history.json(), recommendations: await recommendations.json(), feedback: await feedback.json() };
    });
    expect(clearedData.history).toHaveLength(0);
    expect(clearedData.recommendations).toHaveLength(0);
    expect(clearedData.feedback.length).toBeGreaterThan(0);
    await page.locator('#logoutButton').click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await page.locator('#loginForm [name=email]').fill(account.email);
    await page.locator('#loginForm [name=password]').fill(account.password);
    await page.locator('#loginForm button[type=submit]').click();
    await expect(page.locator('#appShell')).toBeVisible({ timeout: 20_000 });
    const persistedFeedback = await page.evaluate(async () => {
      const response = await fetch('/api/users/me/feedback');
      return response.ok ? response.json() : [];
    });
    expect(persistedFeedback.some(entry => Number(entry.rating) === 5)).toBeTruthy();
    await page.locator('.nav-link[data-panel="activityPanel"]').click();
    const deletionResponsePromise = page.waitForResponse(response =>
      response.url().endsWith('/api/users/me') && response.request().method() === 'DELETE');
    await page.locator('#deleteAccountButton').click();
    const deletionResponse = await deletionResponsePromise;
    expect(deletionResponse.status()).toBe(204);
    expect(deletionConfirmationSeen).toBeTruthy();
    await expect(page.locator('#appShell')).toBeHidden();
    await expect(page.locator('#landingPage')).toBeVisible();
    await page.locator('[data-auth="login"]').first().click();
    await expect(page.locator('#authPanel')).toBeVisible();
    await page.locator('#loginForm [name=email]').fill(account.email);
    await page.locator('#loginForm [name=password]').fill(account.password);
    await page.locator('#loginForm button[type=submit]').click();
    await expect(page.locator('#authMessage')).toContainText('Email or password is incorrect.');
    const deletedAccountCannotRestoreSession = await page.evaluate(async () => {
      const response = await fetch('/api/users/me');
      return response.status === 401;
    });
    expect(deletedAccountCannotRestoreSession).toBeTruthy();
    expect(pageErrors).toEqual([]);
    expect(consoleErrors).toEqual([]);
    expect(serverErrors).toEqual([]);
  } finally {
    if (recoveryContext) await recoveryContext.close();
    // The browser journey owns this synthetic account and runs against an isolated,
    // disposable MySQL volume. It never targets a real user's data.
  }
});

test('a new user sees an honest cold-start message before any behavior exists', async ({ page }) => {
  test.setTimeout(45_000);
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  await page.goto('/');
  await page.locator('[data-auth="register"]').first().click();
  await expect(page.locator('#registerForm')).toBeVisible();
  await page.locator('#registerForm [name=displayName]').fill('Cold start browser test');
  await page.locator('#registerForm [name=username]').fill(`coldstart_${suffix}`);
  await page.locator('#registerForm [name=email]').fill(`coldstart_${suffix}@example.test`);
  await page.locator('#registerForm [name=password]').fill('Test-only-Pass-482!');
  await page.getByRole('button', { name: /Create your account/ }).click();
  await expect(page.locator('#onboardingPanel')).toHaveClass(/active/, { timeout: 20_000 });
  await expect.poll(async () => page.locator('#onboardingNiche option').count()).toBeGreaterThan(1);
  await page.locator('#onboardingPurpose').selectOption('LEARN');
  await page.locator('#onboardingNiche').selectOption({ index: 1 });

  await page.getByRole('button', { name: /Build my Explore feed/ }).click();
  await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/, { timeout: 20_000 });
  const profile = await page.evaluate(async () => {
    const response = await fetch('/api/users/me/recommendation-profile');
    return response.json();
  });
  expect(profile.interactionCount).toBe(0);
  expect(profile.feedbackCount).toBe(0);
  await expect(page.locator('#recommendationEvidenceNote')).toBeVisible();
  await expect(page.locator('#recommendationEvidenceNote'))
    .toContainText('no interaction or rating history yet');
  await expect(page.locator('#recommendationEvidenceNote'))
    .toContainText('onboarding choices and editor-selected resources');

  await page.route('**/api/users/me/recommendations?limit=10', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ status: 201, contentType: 'application/json', body: '[]' });
    } else await route.continue();
  });
  await page.locator('#generateRecommendations').click();
  await expect(page.locator('#recommendationEvidenceNote'))
    .toContainText('general catalog discovery');
  await expect(page.locator('#recommendationList .feed-bucket-title'))
    .toContainText('not personalized');
  await expect(page.locator('#recommendationList .item-card').first()).toBeVisible();
});
