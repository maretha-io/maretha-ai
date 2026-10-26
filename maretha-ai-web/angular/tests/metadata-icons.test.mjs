import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const canonical = readFileSync(new URL('../../src/main/resources/web/nuxeo.war/ui/cp-custom-icons.html', import.meta.url), 'utf8');
const angular = readFileSync(new URL('../src/lib/features/interactions/cp-metadata-icon.component.ts', import.meta.url), 'utf8');

test('metadata trigger and menu use the complete canonical CP SVG glyphs', () => {
  for (const id of ['ai-metadata', 'ai-metadata-update', 'ai-metadata-missing']) {
    const element = canonical.match(new RegExp(`<svg id="${id}"[\\s\\S]*?</svg>`))[0];
    const paths = [...element.matchAll(/<path\s+d="([^"]+)"/g)].map(match => match[1]);
    const branch = angular.match(new RegExp(`@case \\('${id}'\\) \\{([\\s\\S]*?)\\}`))[1];
    assert.deepEqual([...branch.matchAll(/<path d="([^"]+)"/g)].map(match => match[1]), paths);
  }
  assert.match(angular, /aria-hidden="true"/);
});
