// @ts-check
const { test, expect } = require('@playwright/test');

// Tests run sequentially against an isolated E2E server
// (port 3001, {{top/ns}}-e2e.db — separate from the dev server).
// The database is wiped automatically on each run.
//
// Run: npm run e2e
// Run headed: npm run e2e:headed

test.describe.configure({ mode: 'serial' });

const ADMIN_EMAIL = 'alice@example.com';
const ADMIN_PASSWORD = 'admin1234';
const CASHIER_EMAIL = 'bob@example.com';
const CASHIER_PASSWORD = 'cashier1234';

/** Log in as a specific user via the login form. */
async function loginAs(page, email, password) {
  await page.goto('/');
  const loginHeading = page.locator('h2:has-text("Login")');
  const logoutBtn = page.locator('button:has-text("Logout")');

  const which = await Promise.race([
    loginHeading.waitFor({ timeout: 10000 }).then(() => 'login'),
    logoutBtn.waitFor({ timeout: 10000 }).then(() => 'ready'),
  ]);

  if (which === 'ready') {
    return;
  }

  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', password);
  await page.click('button:has-text("Sign In")');
  await expect(logoutBtn).toBeVisible({ timeout: 10000 });
}

/** Log in as the admin user. */
async function loginAsAdmin(page) {
  await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
}

/** Navigate to page, logging in as admin first if needed. */
async function gotoReady(page, navText) {
  await loginAsAdmin(page);
  if (navText) {
    await page.click(`button.nav-btn:has-text("${navText}")`);
  }
}

test.describe('POS1 E2E', () => {

  // --- Setup ---

  test('shows setup page on fresh database', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h2')).toHaveText('System Setup');
    await expect(page.locator('button.nav-btn:has-text("Setup")'))
      .toBeVisible();
  });

  test('initializes the system', async ({ page }) => {
    await page.goto('/');
    await page.fill('input[type="text"]', 'Alice Admin');
    await page.fill('input[type="email"]', ADMIN_EMAIL);
    await page.fill('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button:has-text("Initialize System")');

    await expect(page.locator('.alert.alert-success'))
      .toHaveText(/System initialized/);
    await expect(page.locator('h2')).toHaveText('Login');
  });

  // --- Auth ---

  test('shows login page after setup', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h2')).toHaveText('Login');
    await expect(page.locator('button:has-text("Sign In")'))
      .toBeVisible();
  });

  test('rejects login with wrong password', async ({ page }) => {
    await page.goto('/');
    await page.fill('input[type="email"]', ADMIN_EMAIL);
    await page.fill('input[type="password"]', 'wrongpassword');
    await page.click('button:has-text("Sign In")');

    await expect(page.locator('.alert.alert-error')).toBeVisible();
  });

  test('logs in with correct credentials', async ({ page }) => {
    await page.goto('/');
    await page.fill('input[type="email"]', ADMIN_EMAIL);
    await page.fill('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button:has-text("Sign In")');

    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toBeVisible({ timeout: 10000 });
    await expect(page.locator('h2')).toHaveText('Accounts');
    await expect(page.locator('.subtitle:has-text("Alice Admin")'))
      .toBeVisible();
    await expect(page.locator('button:has-text("Logout")'))
      .toBeVisible();
  });

  test('logs out and returns to login page', async ({ page }) => {
    await loginAsAdmin(page);
    await page.click('button:has-text("Logout")');

    await expect(page.locator('h2')).toHaveText('Login');
    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toHaveCount(0);
  });

  test('session persists across page reload', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toBeVisible();

    await page.reload();
    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toBeVisible({ timeout: 10000 });
    await expect(page.locator('.subtitle:has-text("Alice Admin")'))
      .toBeVisible();
  });

  // --- Accounts ---

  test('accounts page shows the admin', async ({ page }) => {
    await gotoReady(page, 'Accounts');
    await expect(page.locator('h2')).toHaveText('Accounts');

    const table = page.locator('.table');
    await expect(table).toBeVisible();
    await expect(table.locator('td:has-text("Alice Admin")'))
      .toBeVisible();
    await expect(table.locator('td.capitalize:has-text("admin")'))
      .toBeVisible();
  });

  test('creates a cashier account', async ({ page }) => {
    await gotoReady(page, 'Accounts');
    await page.click('button:has-text("New Account")');
    await expect(page.locator('h2')).toHaveText('Create Account');

    await page.fill('input[type="text"]', 'Bob Cashier');
    await page.fill('input[type="email"]', CASHIER_EMAIL);
    await page.selectOption('select', 'cashier');
    await page.fill('input[type="password"]', CASHIER_PASSWORD);
    await page.click('form button[type="submit"]');

    await expect(page.locator('.alert.alert-success'))
      .toHaveText(/Account created/);
  });

  test('creates a manager account', async ({ page }) => {
    await gotoReady(page, 'Accounts');
    await page.click('button:has-text("New Account")');
    await expect(page.locator('h2')).toHaveText('Create Account');

    await page.fill('input[type="text"]', 'Carol Manager');
    await page.fill('input[type="email"]', 'carol@example.com');
    await page.selectOption('select', 'manager');
    await page.fill('input[type="password"]', 'manager1234');
    await page.click('form button[type="submit"]');

    await expect(page.locator('.alert.alert-success'))
      .toHaveText(/Account created/);
  });

  test('accounts page lists all three accounts', async ({ page }) => {
    await gotoReady(page, 'Accounts');

    const rows = page.locator('.table tbody tr');
    await expect(rows).toHaveCount(3);
    await expect(page.locator('td:has-text("Alice Admin")'))
      .toBeVisible();
    await expect(page.locator('td:has-text("Bob Cashier")'))
      .toBeVisible();
    await expect(page.locator('td:has-text("Carol Manager")'))
      .toBeVisible();
  });

  test('disables a cashier account', async ({ page }) => {
    await gotoReady(page, 'Accounts');

    const bobRow = page.locator('tr', {
      has: page.locator('td:has-text("Bob Cashier")')
    });
    await bobRow.locator('button:has-text("Disable")').click();

    await expect(page.locator('.alert.alert-success'))
      .toHaveText(/Account disabled/);
    await expect(bobRow.locator('.badge:has-text("disabled")'))
      .toBeVisible();
  });

  test('re-enables a disabled account', async ({ page }) => {
    await gotoReady(page, 'Accounts');

    const bobRow = page.locator('tr', {
      has: page.locator('td:has-text("Bob Cashier")')
    });
    await bobRow.locator('button:has-text("Enable")').click();

    await expect(page.locator('.alert.alert-success'))
      .toHaveText(/Account enabled/);
    await expect(bobRow.locator('.badge:has-text("active")'))
      .toBeVisible();
  });

  test('admin account has no disable button', async ({ page }) => {
    await gotoReady(page, 'Accounts');

    const adminRow = page.locator('tr', {
      has: page.locator('td:has-text("Alice Admin")')
    });
    await expect(adminRow.locator('button')).toHaveCount(0);
  });

  test('API rejects admin self-disable', async ({ page }) => {
    await loginAsAdmin(page);

    const resp = await page.evaluate(async () => {
      const token = localStorage.getItem('session-token');
      const r = await fetch('/api/accounts/1/disable', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + token,
        },
      });
      return { status: r.status, body: await r.json() };
    });

    expect(resp.status).toBe(400);
    expect(resp.body.error).toBe('Cannot disable your own account');
  });

  // --- Role-based access control ---

  test('cashier cannot access admin endpoints', async ({ page }) => {
    // Log out admin first
    await loginAsAdmin(page);
    await page.click('button:has-text("Logout")');
    await expect(page.locator('h2:has-text("Login")')).toBeVisible();

    await loginAs(page, CASHIER_EMAIL, CASHIER_PASSWORD);

    // Cashier should NOT see Accounts nav
    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toHaveCount(0);

    // API calls to admin endpoints should be forbidden
    const results = await page.evaluate(async () => {
      const token = localStorage.getItem('session-token');
      const headers = {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token,
      };
      const get = (/** @type {string} */ url) =>
        fetch(url, { headers }).then(async r =>
          ({ status: r.status }));
      const put = (/** @type {string} */ url) =>
        fetch(url, { method: 'PUT', headers }).then(async r =>
          ({ status: r.status }));

      return {
        accounts: await get('/api/accounts'),
        disable:  await put('/api/accounts/2/disable'),
      };
    });

    expect(results.accounts.status).toBe(403);
    expect(results.disable.status).toBe(403);
  });

  test('log back in as admin for remaining tests', async ({ page }) => {
    await page.goto('/');
    const logoutBtn = page.locator('button:has-text("Logout")');
    const isLoggedIn = await logoutBtn.isVisible().catch(() => false);
    if (isLoggedIn) {
      await logoutBtn.click();
      await expect(page.locator('h2:has-text("Login")')).toBeVisible();
    }
    await loginAsAdmin(page);
    await expect(page.locator('button.nav-btn:has-text("Accounts")'))
      .toBeVisible({ timeout: 10000 });
  });
});
