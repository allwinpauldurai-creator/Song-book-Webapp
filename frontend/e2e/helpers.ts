import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

// Dev admin from LocalDevApplication (bootstrap mobile +919999999999).
export const ADMIN = { mobile: '99999 99999', password: 'ChangeMe-Admin-123' };

export async function loginViaUi(page: Page, user = ADMIN) {
  await page.goto('/login');
  await page.getByLabel('Mobile number').fill(user.mobile);
  await page.locator('input[autocomplete="current-password"]').fill(user.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/me$/);
}

/** Opens a book's detail page by searching the public catalogue for its title. */
export async function openBook(page: Page, title: string) {
  await page.goto(`/books?q=${encodeURIComponent(title)}`);
  await page.getByRole('link', { name: new RegExp(title) }).first().click();
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
}

/** WCAG 2.1 A/AA scan; returns readable violation lines (empty when clean). */
export async function a11yViolations(page: Page): Promise<string[]> {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  return result.violations.map(
    (v) => `[${v.impact}] ${v.id}: ${v.help} (${v.nodes.length}x) e.g. ${v.nodes[0]?.target.join(' ')}`,
  );
}

/**
 * Waits until the page has rendered its data: no MUI loading skeletons/spinners and every image
 * finished loading. (More reliable than 'networkidle', which a dev server can keep busy.)
 */
export async function settled(page: Page) {
  await page.waitForFunction(
    () =>
      !document.querySelector('.MuiSkeleton-root, .MuiCircularProgress-root') &&
      [...document.images].every((img) => img.complete),
    undefined,
    { timeout: 15_000 },
  );
}

/**
 * Records Content-Security-Policy violations (the browser's securitypolicyviolation events) so a
 * test run against the production build fails if the CSP blocks anything the app needs.
 */
export async function trackCspViolations(page: Page): Promise<string[]> {
  const violations: string[] = [];
  await page.exposeFunction('__reportCsp', (v: string) => violations.push(v));
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (e) =>
      (window as unknown as { __reportCsp: (v: string) => void }).__reportCsp(
        `${e.effectiveDirective} blocked ${e.blockedURI || '(inline)'} on ${location.pathname}`,
      ),
    );
  });
  return violations;
}
