import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { planImport, publicationSql } from '../scripts/catalog-import.mjs';

async function fixtures(t) {
  const assets = await mkdtemp(path.join(tmpdir(), 'afterform-import-'));
  t.after(() => rm(assets, { recursive: true, force: true }));
  await writeFile(path.join(assets, 'poster.webp'), 'image fixture');
  await writeFile(path.join(assets, 'clip.mp4'), 'video fixture');
  const sourceBase = 'https://shop.example/assets';
  const bucketBase = 'https://storage.example/afterform-media';
  const catalog = {
    store: { slug: 'afterform', name: 'Afterform', hero: './assets/poster.webp' },
    products: [{
      id: 'jacket', name: 'Jacket', price: 180, image: sourceBase + '/poster.webp',
      description: 'Colorway ./assets/poster.webp',
      gallery: [{ video: '/assets/clip.mp4', image: 'https://external.example/poster.webp' }]
    }]
  };
  return { catalog, assets, sourceBase, bucketBase, expectedVersion: '7' };
}

test('maps trusted media by SHA-256 and preserves external URLs and other fields', async t => {
  const options = await fixtures(t);
  const plan = await planImport(options);
  const [poster, clip] = plan.objects;
  assert.match(poster.key, /^catalog\/afterform\/[a-f0-9]{64}\/poster\.webp$/);
  assert.equal(poster.contentType, 'image/webp');
  assert.deepEqual(poster.previousUrls, ['./assets/poster.webp', 'https://shop.example/assets/poster.webp']);
  assert.equal(clip.contentType, 'video/mp4');
  assert.equal(plan.snapshot.store.hero, poster.url);
  assert.equal(plan.snapshot.products[0].image, poster.url);
  assert.equal(plan.snapshot.products[0].gallery[0].video, clip.url);
  assert.equal(plan.snapshot.products[0].gallery[0].image, 'https://external.example/poster.webp');
  assert.equal(plan.snapshot.products[0].description, options.catalog.products[0].description);
  assert.deepEqual(await planImport(options), plan);
});

test('rejects escaping the asset directory, invalid products and an invalid publication version', async t => {
  const options = await fixtures(t);
  await assert.rejects(planImport({ ...options, expectedVersion: '7; SELECT 1' }), /positive integer/);
  options.catalog.store.hero = './assets/../poster.webp';
  await assert.rejects(planImport(options), /Unsupported local media reference/);
  options.catalog.products.push(options.catalog.products[0]);
  await assert.rejects(planImport(options), /duplicate product/);
});

test('publication locks the store and only advances from the expected version', async t => {
  const plan = await planImport(await fixtures(t));
  const sql = publicationSql(plan);
  assert.match(sql, /FOR UPDATE/);
  assert.match(sql, /payload = new_payload/);
  assert.match(sql, /IS DISTINCT FROM 7::bigint/);
  assert.match(sql, /INSERT INTO catalog\.publications/);
  assert.match(sql, /WHERE store_id = target\.id AND media_urls \? media_url/);
});
