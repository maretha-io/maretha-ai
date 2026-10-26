import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

// The pure interaction rules are tested directly; this avoids requiring a browser
// to validate server response parsing, permissions, and relation aggregation.
const source = readFileSync(new URL('../src/lib/shared/utils/interaction-utils.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText;
const { cpParameterVisible, formatResponse, responsePresentation, mergeSavedInteractions, hasGroup, isBulkDocument } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

test('chat retains editable context fields while hiding read-only memory parameters', () => {
  assert.equal(cpParameterVisible({ readOnly: false, isContextMemoryParameter: true }, true), true);
  assert.equal(cpParameterVisible({ readOnly: true, isContextMemoryParameter: true }, true), false);
  assert.equal(cpParameterVisible({ readOnly: false, isContextMemoryParameter: false }, true), true);
  assert.equal(cpParameterVisible({ readOnly: true, isContextMemoryParameter: false }, false), true);
  assert.equal(cpParameterVisible({ readOnly: false, isContextMemoryParameter: true }, false), false);
});

test('formats nested and double-encoded JSON without interpreting model HTML', () => {
  const value = { summary: 'Line one\nLine two', tags: ['one', 'two'], nested: { count: 3 } };
  assert.equal(formatResponse(JSON.stringify(value)), JSON.stringify(value, null, 2));
  assert.equal(formatResponse(JSON.stringify(JSON.stringify(value))), JSON.stringify(value, null, 2));
  assert.equal(formatResponse('<img src=x onerror=alert(1)>'), '<img src=x onerror=alert(1)>');
  assert.equal(formatResponse('line one\\nline two'), 'line one\nline two');
});

test('combines source metadata and non-trashed related results, newest first, without mutating either', () => {
  const source = [{ id: 'old', name: 'Old', response: 'one', createdAt: '2026-10-20T12:00:00Z' }];
  const related = [
    { uid: 'saved', type: 'InteractionResults', title: 'Saved response', isTrashed: false, properties: { 'interactionresults:interactions': [{ id: 'new', name: 'New', response: '{}', createdAt: '2026-10-24T12:00:00Z' }] } },
    { uid: 'trash', type: 'InteractionResults', isTrashed: true, properties: { 'interactionresults:interactions': [{ id: 'deleted', createdAt: '2027-01-01' }] } },
    { uid: 'file', type: 'File', properties: { 'interactionresults:interactions': [{ id: 'unrelated', createdAt: '2027-01-01' }] } },
  ];
  const before = JSON.stringify({ source, related });
  const results = mergeSavedInteractions(source, related);
  assert.deepEqual(results.map(item => item.id), ['new', 'old']);
  assert.equal(results[0].doc.uid, 'saved');
  assert.equal(JSON.stringify({ source, related }), before);
});

test('HTML is routed through sanitization while JSON fields containing markup remain text', () => {
  const malicious = '<p onclick="attack()">Safe <strong>result</strong><script>attack()</script></p>';
  const inputs = [];
  const sanitized = responsePresentation(malicious, html => { inputs.push(html); return '<p>Safe <strong>result</strong></p>'; });
  assert.deepEqual(inputs, [malicious]);
  assert.equal(sanitized.html, '<p>Safe <strong>result</strong></p>');
  assert.equal(responsePresentation({ result: malicious }, () => { throw new Error('JSON must remain text'); }).html, null);
  assert.equal(responsePresentation('Plain response', () => { throw new Error('Plain text does not need HTML rendering'); }).html, null);
  assert.equal(responsePresentation('<script>attack()</script>', () => null).html, '');
});

test('AI groups support SDK users and raw Nuxeo groups, and admins bypass group gates', () => {
  assert.equal(hasGroup({ groups: ['AI-CHAT'] }, 'AI-CHAT'), true);
  assert.equal(hasGroup({ groups: ['AI-CHAT'] }, 'AI-METADATA'), false);
  assert.equal(hasGroup({ extendedGroups: [{ name: 'AI-METADATA' }] }, 'AI-METADATA'), true);
  assert.equal(hasGroup({ groups: ['administrators'] }, 'AI-INTERACTION'), true);
  assert.equal(hasGroup({ isAdministrator: true }, 'AI-CHAT'), true);
  assert.equal(hasGroup(null, 'AI-CHAT'), false);
});

test('folder and collection metadata responses are bulk job identifiers', () => {
  assert.equal(isBulkDocument({ type: 'Folder' }), true);
  assert.equal(isBulkDocument({ type: 'CustomContainer', facets: ['Folderish'] }), true);
  assert.equal(isBulkDocument({ type: 'Collection', facets: ['Collection'] }), true);
  assert.equal(isBulkDocument({ type: 'File', facets: ['InteractionResultsFacet'] }), false);
});
