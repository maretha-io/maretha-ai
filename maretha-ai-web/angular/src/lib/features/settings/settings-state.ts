/** Mutable editor drafts. Transport values are cloned before binding to form controls. */
export interface ParameterDraft {
  name: string; type: string; format: string; description: string; value: string;
  readOnly: boolean; enableDocSelection: boolean; isMainDocument: boolean;
  selectionReadOnly: boolean; isContextMemoryParameter: boolean; isAdditionalContext: boolean;
  selectedDocumentIds: string[]; selectedDocumentId: string;
  [key: string]: unknown;
}
export interface InteractionDraft {
  id: string; name: string; description: string; prompt: string; model: string;
  activeOnDocumentTypes: string[]; parameters: ParameterDraft[];
  active: boolean; activeOnActionMenu: boolean; activeOnFolder: boolean; chat: boolean;
  saveMetadata: boolean; mainMetadata: boolean; sendParentsContext: boolean;
  sendAttachments: boolean; requiresUserInput: boolean;
  [key: string]: unknown;
}
export interface EventDraft {
  docType: string; events: string[]; interactions: string[]; active: boolean;
  [key: string]: unknown;
}
export interface SettingsDraft {
  interactions: InteractionDraft[]; eventsSettings: EventDraft[];
  aiSettings: Record<string, unknown>; esSettings: Record<string, unknown>;
  awsS3AccessEnabled: boolean;
}
export interface SchemaFieldDraft {
  xpath: string; type: string; directoryName?: string; multiValue?: boolean;
  active: boolean; additionalContext: boolean; inIsolation: boolean;
  interactionId: string; description: string;
  [key: string]: unknown;
}
export interface SchemaDraft {
  name: string; docType: string; fields: SchemaFieldDraft[];
  [key: string]: unknown;
}
export interface TypeSchemas { name: string; schemas: SchemaDraft[]; }
export const EVENT_CHOICES = [
  { id: 'documentCreated', label: 'Document Created' },
  { id: 'pictureViewsGenerationDone', label: 'Rendition Created' },
  { id: 'beforeDocumentModification', label: 'Document Modified' },
] as const;
export const MODEL_CHOICES = [
  'cohere.command-r-v1:0',
  'anthropic.claude-3-sonnet-20240229-v1:0',
  'anthropic.claude-3-5-sonnet-20241022-v2:0',
  'anthropic.claude-3-7-sonnet-20250219-v1:0',
] as const;
export const PARAMETER_TYPES = ['string', 'number', 'boolean', 'array', 'object'] as const;
export const ES_FIELDS = ['BOOST', 'TEXT_WEIGHT', 'IMAGE_WEIGHT', 'SCORE_MODE', 'BOOST_MODE'] as const;
export function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T; }
export function newParameter(): ParameterDraft {
  return { name: '', type: 'string', format: 'text', description: '', value: '',
    readOnly: false, enableDocSelection: false, isMainDocument: false, selectionReadOnly: false,
    isContextMemoryParameter: false, isAdditionalContext: false, selectedDocumentIds: [], selectedDocumentId: '' };
}
export function newInteraction(name: string, id: string): InteractionDraft {
  return { id, name: name.trim(), description: '', prompt: '', model: '', activeOnDocumentTypes: [],
    parameters: [], active: false, activeOnActionMenu: false, activeOnFolder: false,
    chat: false, saveMetadata: false, mainMetadata: false, sendParentsContext: false,
    sendAttachments: false, requiresUserInput: false, version: '1' };
}
export function normalizeSettings(value: Partial<SettingsDraft> | null): SettingsDraft {
  const settings = clone(value ?? {});
  return {
    interactions: (settings.interactions ?? []).map(item => ({ ...newInteraction(item.name ?? '', String(item.id)),
      ...item, id: String(item.id), activeOnDocumentTypes: item.activeOnDocumentTypes ?? [],
      parameters: (item.parameters ?? []).map(param => ({ ...newParameter(), ...param,
        selectedDocumentIds: param.selectedDocumentIds ?? [] })) })),
    eventsSettings: (settings.eventsSettings ?? []).map(item => ({ ...item,
      events: item.events ?? [], interactions: (item.interactions ?? []).map(String), active: !!item.active })),
    aiSettings: settings.aiSettings ?? {}, esSettings: settings.esSettings ?? {},
    awsS3AccessEnabled: !!settings.awsS3AccessEnabled,
  };
}
export function validEvent(item: EventDraft): boolean {
  return !!item.docType && item.events.length > 0 && item.interactions.length > 0;
}
/** Omit UI-only flags while retaining all server extension properties. */
export function settingsPayload(value: SettingsDraft): Record<string, unknown> {
  return {
    ...clone(value),
    interactions: value.interactions.map(item => {
      const { _dirty, ...interaction } = clone(item);
      return interaction;
    }),
    eventsSettings: value.eventsSettings.map(item => {
      const { invalid, ...event } = clone(item);
      return event;
    }),
  };
}
export function schemaKey(schema: Pick<SchemaDraft, 'name' | 'docType'>): string {
  return JSON.stringify([schema.docType, schema.name]);
}

/** NXQL uses backslash escaping for string literals; never interpolate unescaped user input. */
export function nxqlSearchLiteral(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/[%_]/g, '');
}
