import '@angular/compiler';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';

const source = readFileSync(new URL('../src/lib/features/settings/cp-settings-choices.component.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022, experimentalDecorators: true } }).outputText.replace("'@angular/core'", JSON.stringify(import.meta.resolve('@angular/core')));
const { CpSettingsChoicesComponent } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

test('compact multiple choice edits retain other selections and never mutate the input array', () => {
  const injector = createEnvironmentInjector([], null);
  try {
    const component = runInInjectionContext(injector, () => new CpSettingsChoicesComponent());
    const original = Object.freeze(['first', 'second']);
    component.values = () => original;
    const changes = [];
    component.valuesChange.subscribe(value => changes.push(value));
    component.toggle('third', true);
    component.toggle('first', true);
    component.toggle('first', false);
    assert.deepEqual(changes, [['first', 'second', 'third'], ['first', 'second'], ['second']]);
    assert.deepEqual(original, ['first', 'second']);
    component.disabled = () => true;
    component.toggle('fourth', true);
    assert.equal(changes.length, 3);
  } finally { injector.destroy(); }
});

test('unknown stored IDs remain visible and known IDs use their configured label', () => {
  const injector = createEnvironmentInjector([], null);
  try {
    const component = runInInjectionContext(injector, () => new CpSettingsChoicesComponent());
    component.choices = () => [{ id: 'first', label: 'Document Created' }, 'second'];
    assert.equal(component.title('first'), 'Document Created');
    assert.equal(component.title('second'), 'second');
    assert.equal(component.title('deleted-interaction'), 'deleted-interaction');
  } finally { injector.destroy(); }
});
