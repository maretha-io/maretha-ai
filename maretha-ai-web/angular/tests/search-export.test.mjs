import '@angular/compiler';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createEnvironmentInjector, runInInjectionContext, signal } from '@angular/core';
import { ExtensionRegistry, RESULTS_ACTIONS_SLOT, WebUiAppState, WebUiSlotComponent, defineContribution, provideWebUiExtensions } from '@maretha/webui-sdk';
import { CP_CSV_ACTION_IDS, cpSearchQueryScope } from '../src/lib/features/search/search-state.ts';

test('CSV scope snapshots the entire applied similarity or text query without pagination or selection', () => {
  const vector = [0.1, 0.2];
  const scope = cpSearchQueryScope({ vectorQuery: vector, vectorField: 'similarity', minScore: 0.4 });
  vector.push(0.3);
  assert.deepEqual(scope, { repository: 'default', providerName: 'searchByEmbeddings', queryParams: [],
    namedParameters: { vectorQuery: '[0.1,0.2]', vectorField: 'similarity', min_score: '0.4' } });
  assert.deepEqual(cpSearchQueryScope({ vectorQuery: 'bird', vectorField: 'text', minScore: 0, fulltext: ' annual-report ' }).namedParameters,
    { vectorQuery: 'bird', vectorField: 'text', min_score: '0', ecm_fulltext: 'annual report' });
});

test('empty or invalid queries never provide a scope that could export an unfiltered repository', () => {
  for (const vectorQuery of ['', '   ', [], [NaN], [Infinity]]) {
    assert.equal(cpSearchQueryScope({ vectorQuery, vectorField: typeof vectorQuery === 'string' ? 'text' : 'similarity', minScore: 0.4 }), undefined);
  }
});

test('public query slot keeps CSV available without selection for readers and excludes mutating query actions', () => {
  const user = signal({ id: 'reader', groups: ['AI-SEARCH'], isAdministrator: false });
  const injector = createEnvironmentInjector([ExtensionRegistry, { provide: WebUiAppState, useValue: { user } },
    provideWebUiExtensions({ id: 'test.host.results', contributions: ['maretha.results.csv-export', 'maretha.results.query-trash', 'maretha.results.query-edit'].map(id =>
      defineContribution(RESULTS_ACTIONS_SLOT, { id, when: context => !!context.queryScope, loadComponent: async () => class {} })) }),
  ], null);
  try {
    const context = signal({ document: null, noEdit: true, selectedDocuments: [], items: [], availableCommands: [], execute: async () => {},
      totalCount: 0, loadedCount: 0, allSelected: false, busy: false,
      queryScope: cpSearchQueryScope({ vectorQuery: 'bird', vectorField: 'text', minScore: 0.8 }) });
    const slot = runInInjectionContext(injector, () => new WebUiSlotComponent());
    slot.name = () => 'RESULTS_ACTIONS'; slot.context = context; slot.includeIds = () => CP_CSV_ACTION_IDS;
    // Zero results retain the stock control; its own totalCount/busy guard disables execution.
    assert.deepEqual(slot.visibleItems().map(item => item.id), ['maretha.results.csv-export']);
    assert.equal(slot.slotContext().user.id, 'reader');
    assert.equal(slot.slotContext().noEdit, true);
    assert.deepEqual(slot.slotContext().selectedDocuments, []);
    context.update(value => ({ ...value, totalCount: 20, loadedCount: 2 }));
    assert.deepEqual(slot.visibleItems().map(item => item.id), ['maretha.results.csv-export']);
    context.update(value => ({ ...value, queryScope: undefined }));
    assert.deepEqual(slot.visibleItems(), []);
  } finally { injector.destroy(); }
});
