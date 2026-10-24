import type { DocumentLayoutContext, LayoutField } from '@maretha/webui-sdk';

export const CP_AI_FIELDS: readonly LayoutField[] = Object.freeze([
  { property: 'ai:altText', label: 'Alt Text' },
  { property: 'ai:brand', label: 'Brand' },
  { property: 'ai:seo', label: 'SEO', widget: 'cp-textarea' },
  { property: 'ai:restrictions', label: 'Restrictions', widget: 'cp-textarea' },
  { property: 'ai:elementsDetected', label: 'Elements Detected', widget: 'cp-textarea' },
  { property: 'ai:smartCrops', label: 'Smart Crops', widget: 'cp-textarea' },
  { property: 'ai:classification', label: 'Classification', widget: 'directory', directoryName: 'classification' },
  { property: 'ai:safetyText', label: 'Safety Text', widget: 'cp-textarea' },
  { property: 'ai:safety', label: 'Safety', widget: 'directory', directoryName: 'safetyCategories' },
  { property: 'ai:imageScene', label: 'Image Scene', widget: 'directory', directoryName: 'imageScenes' },
]);

export const CP_DUBLINCORE_FIELDS: readonly LayoutField[] = Object.freeze([
  { property: 'dc:title', label: 'Title', required: true },
  { property: 'dc:description', label: 'Description', widget: 'cp-textarea' },
  { property: 'dc:nature', label: 'Nature', widget: 'directory', directoryName: 'nature' },
  { property: 'dc:subjects', label: 'Subjects', widget: 'directory', directoryName: 'l10nsubjects' },
  { property: 'dc:coverage', label: 'Coverage', widget: 'directory', directoryName: 'l10ncoverage' },
  { property: 'dc:expired', label: 'Expiration date' },
]);

export function cpHasAiSchema(document: Readonly<Record<string, unknown>> | null | undefined): boolean {
  if (!document) return false;
  const facets = document['facets'];
  if (Array.isArray(facets) && facets.includes('InteractionResultsFacet')) return true;
  const schemas = document['schemas'];
  if (Array.isArray(schemas) && schemas.some(schema => schema === 'ai' || schema?.name === 'ai')) return true;
  const properties = document['properties'];
  return !!properties && typeof properties === 'object' && Object.keys(properties).some(name => name.startsWith('ai:'));
}

/** `ai` is a facet layout family, never a fabricated Nuxeo document type. */
export function cpUsesFacetLayout(context: DocumentLayoutContext): boolean {
  return !['file', 'picture', 'interactionresults'].includes(context.documentType || '') &&
    cpHasAiSchema(context.document);
}

/** Every source resource is accounted for, including unchanged native view/import layouts. */
export const CP_LAYOUT_SOURCES = Object.freeze([
  ...['edit', 'metadata', 'view'].map(mode => ({ family: 'InteractionResults', mode, integration: 'cp-layout' })),
  ...['create', 'edit', 'metadata', 'view'].map(mode => ({ family: 'ai', mode, integration: 'facet-layout' })),
  ...['File', 'Picture'].flatMap(family => ['create', 'edit', 'metadata', 'view', 'import'].map(mode => ({
    family, mode, integration: ['create', 'edit', 'metadata'].includes(mode) ? 'native-extra-fields' : 'native-inherited',
  }))),
]);
