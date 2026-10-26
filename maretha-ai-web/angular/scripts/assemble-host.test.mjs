import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { assemble, linkDependencies, stageCanonicalIconFixture } from './assemble-host.mjs';

const script = fileURLToPath(new URL('./prepare-demo-context.sh', import.meta.url));

function fixture(t) {
  const dir = mkdtempSync(path.join(tmpdir(), 'cp-release-test-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  return dir;
}

function zip(file, name, version) {
  execFileSync('python3', ['-c', 'import sys,zipfile,xml.etree.ElementTree as E; z=zipfile.ZipFile(sys.argv[1],"w"); z.writestr("package.xml",E.tostring(E.Element("package",name=sys.argv[2],version=sys.argv[3]))); z.close()', file, name, version]);
}

test('isolated scoped package links do not mutate the shared dependency tree', t => {
  const dir = fixture(t), source = path.join(dir, 'shared'), target = path.join(dir, 'stage');
  mkdirSync(path.join(source, '@maretha', 'webui-sdk'), { recursive: true });
  mkdirSync(path.join(source, 'rxjs'));
  writeFileSync(path.join(source, '@maretha/webui-sdk/package.json'), '{}');
  linkDependencies(source, target);
  mkdirSync(path.join(target, '@maretha/cp-webui'));
  assert.deepEqual(readdirSync(path.join(source, '@maretha')), ['webui-sdk']);
  assert.equal(realpathSync(path.join(target, 'rxjs')), realpathSync(path.join(source, 'rxjs')));
  assert.equal(realpathSync(path.join(target, '@maretha/webui-sdk')), realpathSync(path.join(source, '@maretha/webui-sdk')));
});

test('isolated library glyph tests receive the current canonical Polymer asset', t => {
  const dir = fixture(t), source = path.join(dir, 'addon'), library = path.join(dir, 'cp-library');
  const asset = path.join(source, 'maretha-ai-web/src/main/resources/web/nuxeo.war/ui/cp-custom-icons.html');
  mkdirSync(path.dirname(asset), { recursive: true });
  writeFileSync(asset, '<svg id="ai-metadata">current asset</svg>');
  stageCanonicalIconFixture(source, library);
  assert.equal(readFileSync(path.join(library, 'tests/fixtures/cp-custom-icons.html'), 'utf8'), readFileSync(asset, 'utf8'));
});

test('combined context requires the exact coordinated marketplace versions', t => {
  const dir = fixture(t), addon = path.join(dir, 'addon.zip'), host = path.join(dir, 'host.zip'), context = path.join(dir, 'context');
  zip(addon, 'maretha-ai', '1.0.31'); zip(host, 'maretha-angular-webui', '3.21.5');
  execFileSync('bash', [script, addon, host, context]);
  assert.deepEqual(readdirSync(context).sort(), ['Dockerfile', 'maretha-ai-package-1.0.31.zip', 'maretha-angular-webui-package-3.21.5.zip']);
  assert.deepEqual(readFileSync(path.join(context, 'maretha-ai-package-1.0.31.zip')), readFileSync(addon));
  assert.deepEqual(readFileSync(path.join(context, 'maretha-angular-webui-package-3.21.5.zip')), readFileSync(host));
});

test('context rejects the previous Angular release and avoids overwriting existing output', t => {
  const dir = fixture(t), addon = path.join(dir, 'addon.zip'), host = path.join(dir, 'host.zip'), context = path.join(dir, 'context');
  zip(addon, 'maretha-ai', '1.0.31'); zip(host, 'maretha-angular-webui', '3.21.3');
  assert.notEqual(spawnSync('bash', [script, addon, host, context]).status, 0);
  mkdirSync(context); writeFileSync(path.join(context, 'keep'), 'original');
  zip(host, 'maretha-angular-webui', '3.21.5');
  assert.notEqual(spawnSync('bash', [script, addon, host, context]).status, 0);
  assert.equal(readFileSync(path.join(context, 'keep'), 'utf8'), 'original');
});

test('context rejects the previous add-on release even with the current host', t => {
  const dir = fixture(t), addon = path.join(dir, 'addon.zip'), host = path.join(dir, 'host.zip'), context = path.join(dir, 'context');
  zip(addon, 'maretha-ai', '1.0.29'); zip(host, 'maretha-angular-webui', '3.21.5');
  assert.notEqual(spawnSync('bash', [script, addon, host, context]).status, 0);
});

test('assembly rejects the prior SDK before building either package', t => {
  const dir = fixture(t), source = path.join(dir, 'core');
  mkdirSync(path.join(source, 'app/extensions'), { recursive: true });
  writeFileSync(path.join(source, 'package.json'), JSON.stringify({ version: '3.21.5' }));
  writeFileSync(path.join(source, 'app/extensions/package.json'), JSON.stringify({ version: '0.1.1' }));
  assert.throws(() => assemble(source, path.join(dir, 'assembly')), /Expected WebUI SDK 0.1.2/);
});
