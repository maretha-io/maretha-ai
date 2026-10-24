#!/usr/bin/env node
// Isolated DOM test of the real Angular sanitizer. Uses the host's existing tools.
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const coreRoot = path.resolve(process.argv[2] ?? '');
const require = createRequire(path.join(coreRoot, 'package.json'));
const { build } = require('esbuild');
const { chromium } = require('playwright');
const modelPath = fileURLToPath(new URL('../../angular/src/lib/interactions/interaction-models.ts', import.meta.url));
const dir = mkdtempSync(path.join(tmpdir(), 'cp-rich-response-'));
let browser;
try {
  const bundle = path.join(dir, 'test.js');
  await build({
    stdin: { contents: `
      import '@angular/compiler';
      import { SecurityContext, provideZonelessChangeDetection } from '@angular/core';
      import { createApplication, DomSanitizer } from '@angular/platform-browser';
      import { responsePresentation } from ${JSON.stringify(modelPath)};
      const application = await createApplication({providers:[provideZonelessChangeDetection()]});
      const sanitizer = application.injector.get(DomSanitizer);
      globalThis.cpResponseCheck = value => responsePresentation(value, html => sanitizer.sanitize(SecurityContext.HTML, html));
    `, resolveDir: coreRoot },
    bundle: true, format: 'esm', platform: 'browser', target: 'es2022', outfile: bundle,
  });
  browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage();
  await page.setContent('<!doctype html><html><body></body></html>');
  await page.addScriptTag({ path: bundle, type: 'module' });
  await page.waitForFunction(() => typeof globalThis.cpResponseCheck === 'function');
  const result = await page.evaluate(async () => {
    const content = '<p onclick="globalThis.cpXss=true">Safe <strong>result</strong><a href="https://example.com/legal">Source</a><img src="invalid" onerror="globalThis.cpXss=true"><script>globalThis.cpXss=true</script><a href="javascript:globalThis.cpXss=true">Unsafe</a></p>';
    const response = globalThis.cpResponseCheck(content);
    const element = document.createElement('div'); element.innerHTML = response.html; document.body.append(element);
    await new Promise(resolve => setTimeout(resolve, 20));
    return {
      executed: globalThis.cpXss === true,
      events: element.querySelectorAll('[onclick],[onerror]').length,
      scripts: element.querySelectorAll('script').length,
      executableLinks: element.querySelectorAll('a[href^="javascript:"]').length,
      richText: element.querySelector('strong')?.textContent,
      safeLink: element.querySelector('a')?.getAttribute('href'),
      json: globalThis.cpResponseCheck({ summary: '<strong>JSON data</strong>' }),
    };
  });
  assert.equal(result.executed, false);
  assert.equal(result.events, 0);
  assert.equal(result.scripts, 0);
  assert.equal(result.executableLinks, 0);
  assert.equal(result.richText, 'result');
  assert.equal(result.safeLink, 'https://example.com/legal');
  assert.equal(result.json.html, null);
  assert.match(result.json.text, /<strong>JSON data<\/strong>/);
  console.log('Rich HTML, links, JSON fallback and XSS checks passed with Angular DomSanitizer in Chrome.');
} finally {
  await browser?.close();
  rmSync(dir, { recursive: true, force: true });
}
