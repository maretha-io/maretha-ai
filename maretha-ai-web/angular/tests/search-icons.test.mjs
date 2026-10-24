import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createEnvironmentInjector } from '@angular/core';
import { WebUiIcons } from '@maretha/webui-sdk';
import { CP_SEARCH_ICONS } from '../src/lib/features/search/cp-search-icons.ts';

test('canonical AI search glyph passes the public SVG registry and cleans up with its owner', () => {
  const injector = createEnvironmentInjector([WebUiIcons], null);
  try {
    const icons = injector.get(WebUiIcons);
    const release = icons.register('io.maretha.cp.search', CP_SEARCH_ICONS);
    const icon = icons.catalog()['cp:ai-search'];
    assert.ok(icon);
    assert.equal(icon.viewBox, '0 0 24 24');
    assert.ok(icon.nodes.some(node => node.tag === 'g' && node.children?.length));
    release();
    assert.equal(icons.catalog()['cp:ai-search'], undefined);
  } finally { injector.destroy(); }
});
