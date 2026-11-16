import { expect, test } from '@playwright/test';
import { a11yViolations, openBook, settled } from './helpers';

// Guest flows (FR-01, FR-02): browse without logging in; protected routes send you to login.

test('landing page lists the latest books and searches the catalogue', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Latest books' })).toBeVisible();
  await expect(page.locator('main a[href^="/books/"]').first()).toBeVisible();

  await page.getByRole('search').last().getByRole('textbox').fill('Campfire');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(page).toHaveURL(/\/books\?q=Campfire/);
  await expect(page.getByText('1 book')).toBeVisible();
  await expect(page.getByRole('link', { name: /Campfire Songs/ })).toBeVisible();
});

test('catalogue filters by category and shows an empty state', async ({ page }) => {
  await page.goto('/books');
  await page.getByRole('button', { name: 'Christmas' }).click();
  await expect(page).toHaveURL(/category=/);
  await expect(page.getByRole('link', { name: /Carols by Candlelight/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /Campfire Songs/ })).toHaveCount(0);

  await page.goto('/books?q=zzzz-no-such-book');
  await expect(page.getByText('No books match your search')).toBeVisible();
});

test('drafts and future books are not public', async ({ page }) => {
  await page.goto('/books?q=Untitled%20Draft');
  await expect(page.getByText('No books match your search')).toBeVisible();
  await page.goto('/books?q=Easter');
  await expect(page.getByText('No books match your search')).toBeVisible();
});

test('book detail asks guests to log in, and the reader route redirects to login', async ({ page }) => {
  await openBook(page, 'Morning Hymnal');
  const cta = page.getByRole('link', { name: 'Log in to read' });
  await expect(cta).toBeVisible();
  await expect(page.getByRole('button', { name: /Download/ })).toHaveCount(0);

  await cta.click();
  await expect(page).toHaveURL(/\/login\?next=%2Fread%2F/);
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
});

test('unknown pages show not found', async ({ page }) => {
  await page.goto('/no/such/page');
  await expect(page.getByText('Page not found')).toBeVisible();
});

test.describe('accessibility (WCAG 2.1 AA, axe) — public pages', () => {
  for (const path of ['/', '/books', '/login', '/register', '/forgot-password']) {
    test(`no violations on ${path}`, async ({ page }) => {
      await page.goto(path);
      await settled(page);
      expect(await a11yViolations(page)).toEqual([]);
    });
  }

  test('no violations on a book detail page', async ({ page }) => {
    await openBook(page, 'Morning Hymnal');
    await settled(page);
    expect(await a11yViolations(page)).toEqual([]);
  });
});

test.describe('accessibility in dark mode (follows the OS setting)', () => {
  test.use({ colorScheme: 'dark' });
  for (const path of ['/', '/books', '/login']) {
    test(`no violations on ${path} (dark)`, async ({ page }) => {
      await page.goto(path);
      await settled(page);
      expect(await a11yViolations(page)).toEqual([]);
    });
  }
});

test('PDF.js runtime assets are served (scanned-book image decoders, fonts) and nothing else', async ({ request }) => {
  const wasm = await request.get('/pdfjs/wasm/jbig2.wasm');
  expect(wasm.status()).toBe(200);
  expect(wasm.headers()['content-type']).toBe('application/wasm');
  expect((await request.get('/pdfjs/wasm/openjpeg.wasm')).status()).toBe(200);
  expect((await request.get('/pdfjs/standard_fonts/FoxitSans.pfb')).status()).toBe(200);
  expect((await request.get('/pdfjs/cmaps/Adobe-Japan1-UCS2.bcmap')).status()).toBe(200);
  // Not shipped: PDF form scripting engine. Not reachable: anything outside the allow-list.
  // ('/pdfjs/../x' is normalised by HTTP clients before it reaches the server, so it is not tested here.)
  for (const path of ['/pdfjs/wasm/quickjs-eval.wasm', '/pdfjs/wasm/..%2F..%2Fpackage.json', '/pdfjs/..%2Fpackage.json']) {
    const res = await request.get(path);
    expect(res.headers()['content-type'] ?? '', path).not.toBe('application/wasm');
    expect(await res.text(), path).not.toContain('"pdfjs-dist"');
  }
});
