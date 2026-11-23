import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';

const [apiBaseUrl, frontendPath, storeSlug = 'afterform'] = process.argv.slice(2);
if (!apiBaseUrl || !frontendPath || !/^[a-z0-9][a-z0-9-]{0,62}$/.test(storeSlug)) {
  throw new Error('Usage: storefront-smoke.mjs <API origin> <frontend checkout> [store slug]');
}

const bootstrap = new URL(`/api/v1/public/stores/${storeSlug}/bootstrap`, apiBaseUrl);
const response = await fetch(bootstrap, { signal: AbortSignal.timeout(8000) });
assert.equal(response.status, 200, 'Catalog bootstrap');
assert.match(response.headers.get('etag') ?? '', new RegExp(`^(?:W/)?"store-${storeSlug}-v[0-9]+"$`));
const payload = await response.json();
assert.equal(payload.store.slug, storeSlug);
assert.ok(Array.isArray(payload.products));

const window = {
  location: { search: `?store=${storeSlug}` },
  AF_RUNTIME: { apiBaseUrl, defaultStore: storeSlug, timeoutMs: 8000 }
};
const entries = new Map();
const context = vm.createContext({
  window, fetch, URL, URLSearchParams, AbortController, setTimeout, clearTimeout, console,
  document: { documentElement: { style: { setProperty() {} } } },
  localStorage: {
    getItem: key => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, String(value))
  }
});
for (const file of ['data.js', 'store-loader.js']) {
  vm.runInContext(await readFile(path.join(frontendPath, file), 'utf8'), context, { filename: file });
}
await window.AF.ready;
assert.equal(window.AF.source.mode, 'api', 'storefront avoided demo fallback');
assert.equal(window.AF.source.storeId, storeSlug);
assert.equal(window.AF.catalog().length, payload.products.length);
console.log(`Storefront loaded ${payload.products.length} API products for ${storeSlug}`);
