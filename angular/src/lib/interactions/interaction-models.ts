export interface CpParameter {
  name: string;
  type?: string;
  format?: string;
  description?: string;
  value?: string | number | boolean | null;
  readOnly?: boolean;
  enableDocSelection?: boolean;
  selectionReadOnly?: boolean;
  isContextMemoryParameter?: boolean;
  selectedDocumentIds?: string[];
  [key: string]: unknown;
}

export interface CpInteraction {
  id: string;
  name: string;
  description?: string;
  status?: string;
  saveMetadata?: boolean;
  parameters: CpParameter[];
  [key: string]: unknown;
}

export interface CpMessage {
  role: 'user' | 'assistant';
  content: string;
  date: string;
  param?: string;
  pending?: boolean;
  failed?: boolean;
  interaction?: CpInteraction;
}

export interface CpSavedInteraction {
  id?: string;
  name: string;
  response: unknown;
  createdAt: string;
  doc?: { uid: string; title?: string };
  [key: string]: unknown;
}

/** Responses remain text: model output must never become executable markup. */
export function formatResponse(value: unknown): string {
  let parsed = value;
  for (let i = 0; i < 2 && typeof parsed === 'string'; i++) {
    try { parsed = JSON.parse(parsed.trim()); } catch { break; }
  }
  return typeof parsed === 'string' ? parsed.replace(/\\n/g, '\n') : JSON.stringify(parsed, null, 2) ?? '';
}

/** Rich output crosses Angular's HTML sanitizer; parsed JSON remains visible data. */
export function responsePresentation(value: unknown, sanitize: (html: string) => string | null): { text: string; html: string | null } {
  let decoded = value;
  for (let i = 0; i < 2 && typeof decoded === 'string'; i++) {
    try { decoded = JSON.parse(decoded.trim()); } catch { break; }
  }
  const text = formatResponse(value);
  const isHtml = typeof decoded === 'string' && /<\/?(?:p|div|br|strong|b|em|i|ul|ol|li|a|h[1-6]|table|thead|tbody|tr|th|td|img|span|blockquote|pre|code|hr|script|iframe)\b[^>]*>/i.test(decoded);
  return { text, html: isHtml ? sanitize(text) ?? '' : null };
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (error && typeof error === 'object' && 'message' in error) return String(error.message);
  return 'The request failed. Please try again.';
}

export function isBulkDocument(document: { type?: string; facets?: readonly string[] }): boolean {
  return !!document.facets?.some(f => f === 'Folderish' || f === 'Folder' || f === 'Collection') ||
    ['Folder', 'Workspace', 'Domain', 'Collection', 'OrderedFolder'].includes(document.type ?? '');
}

export function hasGroup(user: unknown, group: string): boolean {
  if (!user || typeof user !== 'object') return false;
  const value = user as Record<string, unknown>;
  return value['isAdministrator'] === true ||
    (Array.isArray(value['groups']) && value['groups'].some(g => g === group || g === 'administrators' || (g && typeof g === 'object' && g.name === group))) ||
    (Array.isArray(value['extendedGroups']) && value['extendedGroups'].some(g => g?.name === group || g?.name === 'administrators'));
}

export function mergeSavedInteractions(source: CpSavedInteraction[], related: Array<{ uid: string; title?: string; type?: string; isTrashed?: boolean; properties: Record<string, any> }>): CpSavedInteraction[] {
  const merged = [...source];
  for (const doc of related) {
    if (doc.type !== 'InteractionResults' || doc.isTrashed === true) continue;
    const results = doc.properties?.['interactionresults:interactions'];
    if (Array.isArray(results)) merged.push(...results.map(item => ({ ...item, doc })));
  }
  return merged.sort((a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0));
}
