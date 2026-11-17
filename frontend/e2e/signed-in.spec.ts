import { expect, test, type Page } from '@playwright/test';
import { a11yViolations, loginViaUi, openBook, settled, trackCspViolations } from './helpers';

// Signed-in flows as the dev admin (FR-15/16/17/18, admin pages). One login for the whole file:
// the backend rate-limits logins per email, and the access token lives only in page memory.
test.describe.configure({ mode: 'serial' });

let page: Page;
let csp: string[] = [];

test.beforeAll(async ({ browser }) => {
  page = await (await browser.newContext({ acceptDownloads: true })).newPage();
  csp = await trackCspViolations(page);
  await loginViaUi(page);
});

test.afterEach(() => {
  expect(csp, 'Content-Security-Policy violations').toEqual([]);
});

test.afterAll(async () => {
  await page.context().close();
});

/** Client-side navigation keeps the in-memory session (a full reload would refresh it via cookie). */
async function go(path: string) {
  await page.evaluate((p) => {
    window.history.pushState({}, '', p);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
  await page.waitForURL((u) => u.pathname + u.search === path);
}

const pageInput = () => page.getByRole('textbox', { name: 'Page number' }).first();

test('dashboard shows the member home', async () => {
  await expect(page.getByRole('heading', { name: /Hello,/ })).toBeVisible();
  await expect(page.getByText('Membership', { exact: true })).toBeVisible();
});

test('reader renders the PDF, turns pages, zooms and searches', async () => {
  await openBook(page, 'Morning Hymnal');
  await page.getByRole('link', { name: 'Read' }).click();
  await expect(page).toHaveURL(/\/read\//);

  const pdfPage = page.getByRole('img', { name: /^Page \d+$/ });
  await expect(pdfPage.locator('canvas')).toBeVisible();
  await expect(page.getByText('/ 12').first()).toBeVisible();

  // Start from page 1 regardless of saved progress.
  await pageInput().fill('1');
  await pageInput().press('Enter');
  await expect(page.getByRole('img', { name: 'Page 1' })).toBeVisible();

  await page.getByRole('button', { name: 'Next page' }).first().click();
  await expect(page.getByRole('img', { name: 'Page 2' })).toBeVisible();
  await expect(pageInput()).toHaveValue('2');

  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('img', { name: 'Page 3' })).toBeVisible();

  const zoom = page.getByText(/^\d+%$/);
  const before = await zoom.textContent();
  await page.getByRole('button', { name: 'Zoom in' }).click();
  await expect(zoom).not.toHaveText(before!);
  await page.getByRole('button', { name: 'Fit width' }).click();

  // The text layer makes text selectable/searchable; the sample PDFs say "Verse 1" on song pages.
  await expect(pdfPage.locator('.textLayer span').first()).toBeAttached();
  await page.getByRole('button', { name: 'Search in book' }).click();
  await page.getByRole('textbox', { name: 'Search text' }).fill('chorus line 2');
  await page.getByRole('button', { name: 'Find' }).click();
  await expect(page.getByText(/11 matches on 11 pages/)).toBeVisible();
  await expect(pdfPage.locator('.textLayer .highlight').first()).toBeVisible();
  await page.getByRole('button', { name: 'Close search' }).click();
});

test('reader accessibility (WCAG 2.1 AA, axe)', async () => {
  // The rendered page itself is a canvas image (role=img with a label); check the reader chrome.
  expect.soft(await a11yViolations(page)).toEqual([]);
});

test('reading progress is saved and restored', async () => {
  await pageInput().fill('5');
  await pageInput().press('Enter');
  await expect(page.getByRole('img', { name: 'Page 5' })).toBeVisible();
  await page.waitForTimeout(2000); // progress saves 1.5 s after the last page turn

  await page.getByRole('link', { name: 'Back to book' }).click();
  await page.getByRole('link', { name: 'Read' }).click();
  await expect(page.getByRole('img', { name: 'Page 5' })).toBeVisible();

  await go('/me');
  await expect(page.getByText('Continue reading')).toBeVisible();
  await expect(page.getByText(/Page 5 ·/).first()).toBeVisible();
});

test('download returns the PDF with a clean file name', async () => {
  await openBook(page, 'Campfire Songs');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: /Download PDF/ }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('Campfire-Songs.pdf');
});

test('profile and requests pages work', async () => {
  await go('/me/profile');
  await expect(page.getByRole('heading', { name: 'Profile', level: 1 })).toBeVisible();
  await expect(page.getByLabel('Email')).not.toBeEditable();
  expect.soft(await a11yViolations(page)).toEqual([]);

  await go('/me/requests');
  await page.getByRole('button', { name: 'New request' }).click();
  const dialog = page.getByRole('dialog', { name: 'New request' });
  await expect(dialog).toBeVisible();
  expect.soft(await a11yViolations(page)).toEqual([]);
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toBeHidden();
});

test.describe('admin pages', () => {
  const pages: [string, RegExp][] = [
    ['/admin', /Admin dashboard/],
    ['/admin/users', /^Users$/],
    ['/admin/memberships', /^Memberships$/],
    ['/admin/books', /^Books$/],
    ['/admin/categories', /^Categories$/],
    ['/admin/requests', /Modification requests/],
    ['/admin/audit', /Audit log/],
  ];
  for (const [path, heading] of pages) {
    test(`${path} loads without accessibility violations`, async () => {
      await go(path);
      await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
      await settled(page);
      expect.soft(await a11yViolations(page)).toEqual([]);
    });
  }

  test('book admin page shows version history', async () => {
    await go('/admin/books?q=Morning');
    await page.getByRole('link', { name: 'Morning Hymnal' }).click();
    await expect(page.getByRole('heading', { name: 'PDF versions' })).toBeVisible();
    await expect(page.getByText('Current', { exact: true })).toBeVisible();
    expect.soft(await a11yViolations(page)).toEqual([]);
  });

  test('new book form validates before upload', async () => {
    await go('/admin/books/new');
    await expect(page.getByRole('button', { name: 'Create book' })).toBeDisabled();
    await page.getByLabel('Title').fill('Playwright draft (not saved)');
    await expect(page.getByRole('button', { name: 'Create book' })).toBeDisabled(); // PDF still required
    expect.soft(await a11yViolations(page)).toEqual([]);
  });
});
