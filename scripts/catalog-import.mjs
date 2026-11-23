import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { createReadStream } from 'node:fs';
import { mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const mediaTypes = {
  '.avif': 'image/avif', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.png': 'image/png', '.webp': 'image/webp', '.mp4': 'video/mp4', '.webm': 'video/webm'
};
const cacheControl = 'public, max-age=31536000, immutable';

export async function planImport({ catalog, expectedVersion, assets, sourceBase, bucketBase }) {
  const slug = catalog.store?.slug;
  if (!/^[a-z0-9][a-z0-9-]{0,62}$/.test(slug ?? '') ||
      typeof catalog.store.name !== 'string' || !catalog.store.name.trim() ||
      !Array.isArray(catalog.products)) {
    throw new Error('Expected a public Catalog bootstrap document with store and products');
  }
  new Intl.NumberFormat(catalog.store.locale ?? 'en-US', {
    style: 'currency', currency: catalog.store.currency ?? 'USD'
  });
  const ids = new Set();
  for (const product of catalog.products) {
    if (!product || !String(product.id || '').trim() || !String(product.name || '').trim() ||
        typeof product.price !== 'number' || !Number.isFinite(product.price) || product.price < 0 ||
        ids.has(String(product.id))) {
      throw new Error('Invalid or duplicate product in import');
    }
    ids.add(String(product.id));
  }
  if (!/^[1-9][0-9]{0,17}$/.test(expectedVersion ?? '')) {
    throw new Error('Expected the current published version as a positive integer');
  }
  const source = baseUrl(sourceBase);
  const bucket = baseUrl(bucketBase);
  const root = await realpath(assets);
  const objects = new Map();
  const snapshot = structuredClone(catalog);

  async function importField(record, field) {
    const value = record?.[field];
    if (typeof value !== 'string') return;
    const local = /^(?:\.\/|\/)?assets\/(.*)$/.exec(value);
    const filename = local?.[1] ?? (value.startsWith(source) ? value.slice(source.length) : undefined);
    if (filename === undefined) return;

    if (!/^[a-z0-9][a-z0-9._-]{0,180}$/i.test(filename) ||
        !Object.hasOwn(mediaTypes, path.extname(filename).toLowerCase())) {
      throw new Error(`Unsupported local media reference: ${value}`);
    }

    if (!objects.has(filename)) {
      const file = await realpath(path.join(root, filename));
      const resolvedName = path.relative(root, file);
      if (resolvedName !== filename &&
          !(process.platform === 'win32' && resolvedName.toLowerCase() === filename.toLowerCase())) {
        throw new Error(`Invalid asset path: ${filename}`);
      }
      const hash = createHash('sha256');
      let size = 0;
      for await (const chunk of createReadStream(file)) {
        size += chunk.length;
        hash.update(chunk);
      }
      if (!size) throw new Error(`Empty media file: ${filename}`);
      const sha256 = hash.digest('hex');
      const key = `catalog/${slug}/${sha256}/${filename}`;
      objects.set(filename, {
        file, key, sha256, size,
        contentType: mediaTypes[path.extname(filename).toLowerCase()],
        url: bucket + key, previousUrls: []
      });
    }
    const object = objects.get(filename);
    if (!object.previousUrls.includes(value)) object.previousUrls.push(value);
    record[field] = object.url;
  }

  await importField(snapshot.store, 'hero');
  await importField(snapshot.home?.journal, 'image');
  for (const slide of snapshot.home?.slides ?? []) await importField(slide, 'image');
  for (const look of snapshot.looks ?? []) await importField(look, 'image');
  for (const product of snapshot.products) {
    await importField(product, 'image');
    for (const frame of product.gallery ?? []) {
      await importField(frame, 'image');
      await importField(frame, 'video');
    }
  }
  return { snapshot, objects: [...objects.values()], expectedVersion };
}

function baseUrl(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
    throw new Error('Media origins must use HTTPS without credentials, queries or fragments');
  }
  return url.href.replace(/\/?$/, '/');
}

function jsonSql(value) {
  const base64 = Buffer.from(JSON.stringify(value)).toString('base64');
  return `convert_from(decode('${base64}', 'base64'), 'UTF8')::jsonb`;
}

export function publicationSql({ snapshot, objects, expectedVersion }) {
  if (!/^[1-9][0-9]{0,17}$/.test(expectedVersion ?? '')) throw new Error('Invalid publication version');
  const replacements = Object.fromEntries(objects.flatMap(({ previousUrls, url }) =>
    previousUrls.map(previous => [previous, url])));
  return `DO $catalog_import$
DECLARE
  target catalog.stores%ROWTYPE;
  new_payload jsonb := ${jsonSql(snapshot)};
  media_urls jsonb := ${jsonSql(replacements)};
  next_version bigint;
BEGIN
  SELECT * INTO STRICT target FROM catalog.stores
    WHERE slug = new_payload #>> '{store,slug}' AND status = 'active' FOR UPDATE;
  IF EXISTS (SELECT 1 FROM catalog.publications
             WHERE store_id = target.id AND version = target.published_version AND payload = new_payload) THEN
    RETURN;
  END IF;
  IF target.published_version IS DISTINCT FROM ${expectedVersion}::bigint THEN
    RAISE EXCEPTION 'Catalog changed since export (current version %)', target.published_version;
  END IF;
  SELECT coalesce(max(version), 0) + 1 INTO next_version
    FROM catalog.publications WHERE store_id = target.id;
  INSERT INTO catalog.publications(store_id, version, payload)
    VALUES (target.id, next_version, new_payload);
  UPDATE catalog.product_media SET media_url = media_urls ->> media_url
    WHERE store_id = target.id AND media_urls ? media_url;
  UPDATE catalog.stores SET published_version = next_version, updated_at = now()
    WHERE id = target.id;
END
$catalog_import$;
`;
}

async function verifyObjects(objects) {
  for (const object of objects) {
    const response = await fetch(object.url, { redirect: 'error', signal: AbortSignal.timeout(60_000) });
    if (!response.ok || response.headers.get('content-type')?.split(';')[0] !== object.contentType) {
      await response.body?.cancel();
      throw new Error(`Unexpected response for ${object.key}: ${response.status}`);
    }
    const sha256 = createHash('sha256');
    let size = 0;
    for await (const chunk of response.body) {
      size += chunk.length;
      if (size > object.size) throw new Error(`Size mismatch for ${object.key}`);
      sha256.update(chunk);
    }
    if (size !== object.size || sha256.digest('hex') !== object.sha256) {
      throw new Error(`Checksum mismatch for ${object.key}`);
    }
  }
}

function uploadObjects(bucketBase, objects) {
  const endpoint = new URL(bucketBase);
  const bucket = endpoint.pathname.match(/^\/([a-z0-9][a-z0-9.-]{2,62})\/?$/)?.[1];
  if (!bucket) throw new Error('Bucket URL must be a Neon storage endpoint followed by one bucket');
  for (const object of objects) {
    const result = spawnSync('aws', [
      's3api', 'put-object', '--endpoint-url', endpoint.origin, '--bucket', bucket,
      '--key', object.key, '--body', object.file, '--content-type', object.contentType,
      '--cache-control', cacheControl
    ], { stdio: 'inherit' });
    if (result.error || result.status !== 0) throw new Error(`Upload failed for ${object.key}`);
  }
}

async function main([action, ...args]) {
  if (action === 'upload' && args.length === 2) {
    const [bucket, manifest] = args;
    uploadObjects(baseUrl(bucket), JSON.parse(await readFile(manifest, 'utf8')));
    return;
  }
  if (action === 'verify' && args.length === 1) {
    const objects = JSON.parse(await readFile(args[0], 'utf8'));
    await verifyObjects(objects);
    console.log(`Verified ${objects.length} public media objects by content type and SHA-256`);
    return;
  }
  if (action !== 'plan' || args.length !== 6) {
    throw new Error('Usage: catalog-import.mjs plan <snapshot.json> <expected-version> <asset-dir> <source-assets-url> <bucket-url> <output-dir> | upload <bucket-url> <manifest.json> | verify <manifest.json>');
  }

  const [input, expectedVersion, assets, sourceBase, bucketBase, output] = args;
  const catalog = JSON.parse(await readFile(input, 'utf8'));
  const plan = await planImport({ catalog, expectedVersion, assets, sourceBase, bucketBase });
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, 'snapshot.json'), JSON.stringify(plan.snapshot, null, 2));
  await writeFile(path.join(output, 'manifest.json'), JSON.stringify(plan.objects, null, 2));
  await writeFile(path.join(output, 'publish.sql'), publicationSql(plan));
  console.log(`Planned ${plan.objects.length} objects for ${catalog.store.slug}, version ${expectedVersion}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
