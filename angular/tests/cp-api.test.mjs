import '@angular/compiler';
import { strict as assert } from 'node:assert';
import { afterEach, beforeEach, test } from 'node:test';
import { PendingTasks, createEnvironmentInjector, signal } from '@angular/core';
import { provideHttpClient, withNoXsrfProtection } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebUiAppState, DocumentLayoutRegistry, DocumentFieldExtensionRegistry, ExtensionRegistry, documentLayoutContext } from '@maretha/webui-sdk';
import { firstValueFrom } from 'rxjs';
import { CpApiService, CP_LAYOUT_SOURCES, cpHasAiSchema, provideCpDocumentLayouts, provideCpWebUi, unwrapCpResponse } from '../dist/fesm2022/maretha-cp-webui.mjs';

let injector;
let http;
let api;
beforeEach(() => {
  injector = createEnvironmentInjector([
    provideHttpClient(withNoXsrfProtection()), provideHttpClientTesting(),
    { provide: PendingTasks, useValue: { add: () => () => {} } },
    { provide: WebUiAppState, useValue: { nuxeoUrl: 'https://nuxeo.example/nuxeo/', user: signal(null) } },
    CpApiService, DocumentLayoutRegistry, provideCpDocumentLayouts(),
  ], null);
  http = injector.get(HttpTestingController);
  api = injector.get(CpApiService);
});
afterEach(() => { http.verify(); injector.destroy(); });

test('automation sends the Nuxeo request envelope and unwraps an object payload', async () => {
  const params = { schemaSettings: { ai: { fields: ['classification'] } } };
  const value = firstValueFrom(api.execute('CP.SaveInteractionSettings', params, { uid: 'document-1' }));
  const request = http.expectOne('https://nuxeo.example/nuxeo/api/v1/automation/CP.SaveInteractionSettings');
  assert.equal(request.request.method, 'POST');
  assert.equal(request.request.headers.get('Content-Type'), 'application/json');
  assert.equal(request.request.headers.get('Accept'), 'application/json');
  assert.deepEqual(request.request.body, { params, input: 'document-1' });
  request.flush({ value: { saved: true } });
  assert.deepEqual(await value, { saved: true });
});

test('raw documents, arrays, false and null remain distinct operation values', () => {
  const document = { uid: 'one', properties: {} };
  assert.equal(unwrapCpResponse(document), document);
  assert.deepEqual(unwrapCpResponse({ value: ['one'] }), ['one']);
  assert.equal(unwrapCpResponse({ value: false }), false);
  assert.equal(unwrapCpResponse({ value: null }), null);
});

test('GetSettings uses the media types accepted by the live Nuxeo automation endpoint', async () => {
  const value = firstValueFrom(api.execute('GetSettingsOperation'));
  const request = http.expectOne('https://nuxeo.example/nuxeo/api/v1/automation/GetSettingsOperation');
  // The deployed LTS endpoint rejects application/json+nxrequest with HTTP 415.
  assert.equal(request.request.headers.get('Content-Type'), 'application/json');
  assert.equal(request.request.headers.get('Accept'), 'application/json');
  assert.deepEqual(request.request.body, { params: {} });
  request.flush({ 'entity-type': 'settings', value: { interactions: [{ id: 'configured' }], awsS3AccessEnabled: false } });
  assert.deepEqual(await value, { interactions: [{ id: 'configured' }], awsS3AccessEnabled: false });
});

test('document paths are encoded without changing their path segments', async () => {
  const value = firstValueFrom(api.getDocument('/default-domain/My Documents/Report #1'));
  const request = http.expectOne('https://nuxeo.example/nuxeo/api/v1/path/default-domain/My%20Documents/Report%20%231');
  assert.equal(request.request.headers.get('properties'), '*');
  request.flush({ uid: 'one', type: 'File', properties: {} });
  assert.equal((await value).uid, 'one');
});

test('provider sends list parameters, zero and false without dropping them', async () => {
  const value = firstValueFrom(api.provider('searchByEmbeddings', { currentPageIndex: 0, enabled: false, selected: ['one', 'two'], omitted: null }));
  const request = http.expectOne(req => req.url.endsWith('/search/pp/searchByEmbeddings/execute'));
  assert.equal(request.request.params.get('currentPageIndex'), '0');
  assert.equal(request.request.params.get('enabled'), 'false');
  assert.deepEqual(request.request.params.getAll('selected'), ['one', 'two']);
  assert.equal(request.request.params.has('omitted'), false);
  request.flush({ entries: [] });
  assert.deepEqual((await value).entries, []);
});

test('large similarity vectors use the same automation POST contract as Nuxeo Elements', async () => {
  const vector = Array.from({ length: 1024 }, (_, index) => index / 1024);
  const value = firstValueFrom(api.provider('searchByEmbeddings', { vectorField: 'similarity', vectorQuery: vector, min_score: 0.4, pageSize: 100, currentPageIndex: 0 }, 'POST'));
  const request = http.expectOne('https://nuxeo.example/nuxeo/api/v1/automation/Repository.PageProvider');
  assert.equal(request.request.method, 'POST');
  assert.equal(request.request.headers.get('Content-Type'), 'application/json');
  assert.equal(request.request.headers.get('Accept'), 'application/json');
  assert.deepEqual(request.request.body.params, { providerName: 'searchByEmbeddings', pageSize: 100, currentPageIndex: 0,
    namedParameters: { vectorField: 'similarity', vectorQuery: JSON.stringify(vector), min_score: '0.4' } });
  assert.match(request.request.headers.get('enrichers-document'), /relevance_score/);
  request.flush({ entries: [] });
  assert.deepEqual((await value).entries, []);
});

test('writes retain the optimistic concurrency token and only changed fields', async () => {
  const value = firstValueFrom(api.saveDocument({ uid: 'one', type: 'File', changeToken: 'revision-4', properties: { unrelated: true } }, { 'ai:altText': 'A bird' }));
  const request = http.expectOne('https://nuxeo.example/nuxeo/api/v1/id/one');
  assert.equal(request.request.method, 'PUT');
  assert.deepEqual(request.request.body, { 'entity-type': 'document', uid: 'one', changeToken: 'revision-4', properties: { 'ai:altText': 'A bird' } });
  request.flush({ uid: 'one', type: 'File', properties: { 'ai:altText': 'A bird' } });
  assert.equal((await value).uid, 'one');
  await assert.rejects(firstValueFrom(api.saveDocument({ uid: 'one', properties: {} }, {})), /change token/);
});

test('facet layouts never invent an ai document type or replace File/Picture viewers', () => {
  const registry = injector.get(DocumentLayoutRegistry);
  assert.equal(CP_LAYOUT_SOURCES.length, 17);
  assert.equal(CP_LAYOUT_SOURCES.filter(source => source.integration === 'native-inherited').length, 4);
  assert.equal(cpHasAiSchema({ facets: ['InteractionResultsFacet'] }), true);
  const file = documentLayoutContext({ documentType: 'File', mode: 'view', document: { facets: ['InteractionResultsFacet'] } });
  assert.equal(registry.resolve(file), undefined);
  const derived = documentLayoutContext({ documentType: 'Contract', mode: 'view', document: { facets: ['InteractionResultsFacet'] } });
  assert.equal(registry.resolve(derived)?.id, 'io.maretha.cp.layout.ai.view');
  const results = documentLayoutContext({ documentType: 'InteractionResults', mode: 'edit' });
  assert.equal(registry.resolve(results)?.id, 'io.maretha.cp.layout.interactionresults.edit');
});

test('public provider registers group gates and adds AI fields only to attached schemas', () => {
  const registryInjector = createEnvironmentInjector([ExtensionRegistry, DocumentLayoutRegistry, DocumentFieldExtensionRegistry, provideCpWebUi()], null);
  try {
    const registry = registryInjector.get(ExtensionRegistry);
    const user = { id: 'member', displayName: 'Member', isAdministrator: false, groups: [] };
    const context = { document: { uid: 'one', type: 'File', facets: ['InteractionResultsFacet'] }, noEdit: false, user };
    assert.equal(registry.resolve('DOCUMENT_ACTIONS', context).items.length, 0);
    assert.equal(registry.resolve('DOCUMENT_VIEWS_ITEMS', context).items.length, 1);
    user.groups = ['AI-METADATA', 'AI-SEARCH'];
    assert.equal(registry.resolve('DOCUMENT_ACTIONS', context).items.length, 1);
    assert.equal(registry.resolve('DOCUMENT_VIEWS_ITEMS', context).items.length, 2);
    const fields = registryInjector.get(DocumentFieldExtensionRegistry);
    assert.equal(fields.fields(documentLayoutContext({ documentType: 'File', mode: 'edit', document: { properties: {} } })).length, 0);
    const attached = fields.fields(documentLayoutContext({ documentType: 'Picture', mode: 'edit', document: { properties: { 'ai:altText': '' } } }));
    assert.equal(attached.length, 10);
    assert.equal(attached.find(field => field.property === 'ai:classification').directoryName, 'classification');
    assert.equal(attached.find(field => field.property === 'ai:safety').directoryName, 'safetyCategories');
    assert.equal(attached.find(field => field.property === 'ai:imageScene').directoryName, 'imageScenes');
  } finally { registryInjector.destroy(); }
});
