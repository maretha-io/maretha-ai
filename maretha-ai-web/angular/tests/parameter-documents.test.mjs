import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';
import { firstValueFrom, of, throwError, Subject } from 'rxjs';

const source = readFileSync(new URL('../src/lib/shared/utils/parameter-documents.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 } }).outputText.replace("'rxjs'", JSON.stringify(import.meta.resolve('rxjs')));
const { parameterDocumentTitles } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

test('stored selections display titles, fetch duplicate IDs once and preserve inaccessible IDs', async () => {
  const calls = [];
  const parameters = [{ selectedDocumentIds: ['known', 'missing'] }, { selectedDocumentIds: ['known', 'dc-title'] }];
  const before = JSON.stringify(parameters);
  const result = await firstValueFrom(parameterDocumentTitles(parameters, uid => {
    calls.push(uid);
    return uid === 'missing' ? throwError(() => new Error('Forbidden')) : of(uid === 'dc-title' ? { properties: { 'dc:title': 'Dublin Core title' } } : { title: 'Selected document' });
  }));
  assert.deepEqual(calls, ['known', 'missing', 'dc-title']);
  assert.deepEqual(result, { known: 'Selected document', missing: 'missing', 'dc-title': 'Dublin Core title' });
  assert.equal(JSON.stringify(parameters), before);
});

test('changing parameter scope cancels pending title reads and empty selections issue no reads', async () => {
  let called = false;
  assert.deepEqual(await firstValueFrom(parameterDocumentTitles([], () => { called = true; })), {});
  assert.equal(called, false);
  const pending = new Subject();
  const subscription = parameterDocumentTitles([{ selectedDocumentIds: ['old-document'] }], () => pending).subscribe(() => assert.fail('A departed scope must not publish titles'));
  assert.equal(pending.observed, true);
  subscription.unsubscribe();
  assert.equal(pending.observed, false);
});
