import type { CpDocument } from '../../shared/types/cp-models';

export interface CpSearchCriteria {
  vectorQuery: string | readonly number[];
  vectorField: 'text' | 'similarity';
  minScore: number;
  fulltext?: string;
}
export function cpConfidence(value: unknown, fallback: number): number {
  if (value === null || value === undefined || value === '') return fallback;
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.min(1, number)) : fallback;
}
export function cpSimilarityVector(document: CpDocument | null | undefined): readonly number[] {
  const image = document?.properties?.['interactionresults:embeddingsImage'];
  const text = document?.properties?.['interactionresults:embeddingsText'];
  const vector = Array.isArray(image) && image.length ? image : text;
  return Array.isArray(vector) && vector.every(value => typeof value === 'number' && Number.isFinite(value)) ? vector : [];
}
export function cpSearchParameters(criteria: CpSearchCriteria, page = 0, sortBy = 'contextParameters.relevance_score', sortOrder = 'desc') {
  return {
    vectorQuery: criteria.vectorQuery,
    vectorField: criteria.vectorField,
    min_score: cpConfidence(criteria.minScore, criteria.vectorField === 'text' ? 0.8 : 0.4),
    ...(criteria.fulltext?.trim() ? { ecm_fulltext: criteria.fulltext.trim().replace(/-/g, ' ') } : {}),
    currentPageIndex: page, pageSize: criteria.vectorField === 'similarity' ? 100 : 40,
    sortBy, sortOrder,
  };
}
export function cpScore(document: CpDocument | Readonly<Record<string, unknown>>): string {
  const context = document['contextParameters'] as Record<string, unknown> | undefined;
  const score = Number(context?.['relevance_score']);
  return Number.isFinite(score) && context?.['relevance_score'] != null ? (Math.round(score * 10) / 10).toString() : '-';
}
