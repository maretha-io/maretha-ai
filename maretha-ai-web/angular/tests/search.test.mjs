import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { cpConfidence, cpSearchParameters, cpSimilarityVector, cpScore } from '../src/lib/features/search/search-state.ts';

test('confidence defaults differ between text and similar queries and preserve zero', () => {
  assert.equal(cpConfidence(undefined, 0.8), 0.8);
  assert.equal(cpConfidence('', 0.4), 0.4);
  assert.equal(cpConfidence('0', 0.8), 0);
  assert.equal(cpConfidence(-2, 0.8), 0);
  assert.equal(cpConfidence(7, 0.8), 1);
});
test('similarity prefers image embeddings and falls back to valid text vectors', () => {
  assert.deepEqual(cpSimilarityVector({ properties: { 'interactionresults:embeddingsImage': [1, 2], 'interactionresults:embeddingsText': [3] } }), [1, 2]);
  assert.deepEqual(cpSimilarityVector({ properties: { 'interactionresults:embeddingsImage': [], 'interactionresults:embeddingsText': [3] } }), [3]);
  assert.deepEqual(cpSimilarityVector({ properties: { 'interactionresults:embeddingsText': ['not a number'] } }), []);
  assert.deepEqual(cpSimilarityVector(null), []);
});
test('provider parameters retain the vector, confidence, filters, page and sorting', () => {
  const text = cpSearchParameters({ vectorQuery: 'seaside', vectorField: 'text', minScore: 0.8, fulltext: '  annual-report ' }, 2, 'dc:title', 'asc');
  assert.deepEqual(text, { vectorQuery: 'seaside', vectorField: 'text', min_score: 0.8, ecm_fulltext: 'annual report', currentPageIndex: 2, pageSize: 40, sortBy: 'dc:title', sortOrder: 'asc' });
  const similar = cpSearchParameters({ vectorQuery: [0.1, 0.2], vectorField: 'similarity', minScore: 0.4 });
  assert.equal(similar.pageSize, 100);
  assert.equal(similar.min_score, 0.4);
  assert.deepEqual(similar.vectorQuery, [0.1, 0.2]);
});
test('relevance renders zero and rounds like the original results layout', () => {
  assert.equal(cpScore({ contextParameters: { relevance_score: 0 } }), '0');
  assert.equal(cpScore({ contextParameters: { relevance_score: 0.84 } }), '0.8');
  assert.equal(cpScore({ contextParameters: {} }), '-');
});
