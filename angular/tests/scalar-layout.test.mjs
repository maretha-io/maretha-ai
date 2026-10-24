import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { DOCUMENT_LAYOUT_HOST, documentLayoutContext } from '@maretha/webui-sdk';
import { CpApiService, CP_DOCUMENT_LAYOUTS } from '../dist/fesm2022/maretha-cp-webui.mjs';

const ScalarLayout = CP_DOCUMENT_LAYOUTS.find(layout => layout.documentType === 'InteractionResults' && layout.mode === 'edit').component;
function fixture(mode = 'edit', properties = {}) {
  let closed = 0;
  const injector = createEnvironmentInjector([
    { provide: CpApiService, useValue: {} },
    { provide: DOCUMENT_LAYOUT_HOST, useValue: { cancel: () => closed++, complete: () => {} } },
  ], null);
  const component = runInInjectionContext(injector, () => new ScalarLayout());
  component.layoutContext = documentLayoutContext({ documentType: 'InteractionResults', mode,
    document: { uid: 'result', type: 'InteractionResults', properties: { 'dc:title': 'Result', ...properties } } });
  component.ngOnChanges();
  return { component, closed: () => closed, destroy: () => { component.ngOnDestroy(); injector.destroy(); } };
}

test('Cancel preserves a refused draft and closes only after the departure guard allows it', async () => {
  const value = fixture();
  try {
    let allow = false;
    value.component.editor = () => ({ canDeactivate: () => allow });
    await value.component.cancel(); assert.equal(value.closed(), 0);
    allow = true;
    await value.component.cancel(); assert.equal(value.closed(), 1);
  } finally { value.destroy(); }
});

test('an async departure result cannot close a replaced or destroyed dialog scope', async () => {
  const value = fixture();
  try {
    let resolve;
    value.component.editor = () => ({ canDeactivate: () => new Promise(accept => resolve = accept) });
    const pending = value.component.cancel();
    value.component.layoutContext = documentLayoutContext({ documentType: 'InteractionResults', mode: 'edit', document: { uid: 'other' } });
    resolve(true); await pending; assert.equal(value.closed(), 0);
    const afterDestroy = value.component.cancel(); value.component.ngOnDestroy(); resolve(true); await afterDestroy;
    assert.equal(value.closed(), 0);
  } finally { value.destroy(); }
});

test('metadata omits empty optional fields and presents descriptions as plain multiline text', () => {
  const value = fixture('metadata', { 'dc:description': 'First line\nSecond line', 'dc:subjects': [], 'dc:nature': '', 'dc:coverage': null });
  try {
    assert.deepEqual(value.component.layout.fields.map(field => field.property), ['dc:title', 'dc:description']);
    assert.equal(value.component.layout.fields.find(field => field.property === 'dc:description').widget, undefined);
    assert.equal(value.component.layout.preserveSchemaConstraints, true);
  } finally { value.destroy(); }
  const editable = fixture('edit');
  try {
    assert.equal(editable.component.layout.fields.length, 6);
    assert.equal(editable.component.layout.fields.find(field => field.property === 'dc:description').widget, 'cp-textarea');
  } finally { editable.destroy(); }
});
