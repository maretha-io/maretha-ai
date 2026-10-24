import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeSettings, settingsPayload, schemaKey, validEvent, nxqlSearchLiteral } from './settings-state.ts';

test('editing settings never mutates server data and preserves extension settings', () => {
  const source = {
    interactions: [{ id: 42, name: 'Summarize', active: true, chat: true,
      requiresUserInput: true, sendParentsContext: true, sendAttachments: true,
      customSetting: 'keep', parameters: [{ name: 'Context', value: 'false', selectedDocumentIds: ['document-one'] }] }],
    eventsSettings: [{ docType: 'Facet_InteractionResultsFacet', events: ['documentCreated'], interactions: [42], active: true }],
    aiSettings: { embeddingModel: 'existing-model', preservedServerOption: 17 },
    esSettings: { BOOST: '0' }, awsS3AccessEnabled: false,
  };
  const before = JSON.stringify(source);
  const draft = normalizeSettings(source);
  assert.equal(draft.interactions[0].id, '42');
  assert.deepEqual(draft.eventsSettings[0].interactions, ['42']);
  draft.interactions[0].parameters[0].selectedDocumentIds.push('document-two');
  draft.aiSettings.embeddingModel = 'edited-model';
  assert.equal(JSON.stringify(source), before);
  const payload = settingsPayload(draft);
  assert.equal(payload.interactions[0].requiresUserInput, true);
  assert.equal(payload.interactions[0].sendParentsContext, true);
  assert.equal(payload.interactions[0].sendAttachments, true);
  assert.equal(payload.interactions[0].customSetting, 'keep');
  assert.equal(payload.aiSettings.preservedServerOption, 17);
  assert.equal(payload.interactions[0].parameters[0].value, 'false');
});

test('save payload includes explicit false and empty arrays so users can clear settings', () => {
  const draft = normalizeSettings({ interactions: [], eventsSettings: [], awsS3AccessEnabled: false });
  const payload = settingsPayload(draft);
  assert.deepEqual(payload.interactions, []);
  assert.deepEqual(payload.eventsSettings, []);
  assert.equal(payload.awsS3AccessEnabled, false);
  assert.equal(Object.hasOwn(payload, 'awsS3AccessEnabled'), true);
});

test('save transport excludes UI flags without losing server properties', () => {
  const draft = normalizeSettings({
    interactions: [{ id: 'interaction', name: 'Metadata', _dirty: true, active: false }],
    eventsSettings: [{ docType: 'File', events: ['documentCreated'], interactions: ['interaction'], active: false, invalid: '' }],
  });
  const payload = settingsPayload(draft);
  assert.equal(Object.hasOwn(payload.interactions[0], '_dirty'), false);
  assert.equal(Object.hasOwn(payload.eventsSettings[0], 'invalid'), false);
  assert.equal(payload.eventsSettings[0].active, false);
  assert.equal(draft.interactions[0]._dirty, true);
});

test('facet and document type mappings for the same schema remain independent', () => {
  const keys = [
    schemaKey({ name: 'ai', docType: 'File' }),
    schemaKey({ name: 'ai', docType: 'Picture' }),
    schemaKey({ name: 'ai', docType: 'Facet_InteractionResultsFacet' }),
    schemaKey({ name: 'other_ai', docType: 'Facet_InteractionResultsFacet' }),
  ];
  assert.equal(new Set(keys).size, 4);
});

test('event mappings require a type, one or more events and one or more interactions', () => {
  const complete = { docType: 'File', events: ['documentCreated'], interactions: ['metadata'], active: false };
  assert.equal(validEvent(complete), true);
  assert.equal(validEvent({ ...complete, docType: '' }), false);
  assert.equal(validEvent({ ...complete, events: [] }), false);
  assert.equal(validEvent({ ...complete, interactions: [] }), false);
});

test('document search escapes NXQL literals and prevents query wildcard expansion', () => {
  assert.equal(nxqlSearchLiteral("O'Reilly"), "O\\'Reilly");
  assert.equal(nxqlSearchLiteral('folder\\file'), 'folder\\\\file');
  assert.equal(nxqlSearchLiteral('%report_'), 'report');
  const hostile = "x' OR ecm:uuid <> 'x";
  assert.equal(nxqlSearchLiteral(hostile), "x\\' OR ecm:uuid <> \\'x");
});
