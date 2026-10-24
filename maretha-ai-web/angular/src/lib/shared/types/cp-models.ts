/** Nuxeo's document envelope; individual schemas remain owned by the server. */
export interface CpDocument {
  uid: string;
  type: string;
  path?: string;
  title?: string;
  changeToken?: string;
  properties: Record<string, any>;
  facets?: readonly string[];
  contextParameters?: Record<string, any>;
  [key: string]: any;
}

export interface CpList<T> {
  entries: T[];
  resultsCount?: number;
  pageCount?: number;
  currentPageIndex?: number;
  pageSize?: number;
  isNextPageAvailable?: boolean;
  [key: string]: any;
}

export interface CpInteractionParameter {
  name: string;
  type?: string;
  format?: string;
  description?: string;
  value?: string;
  readOnly?: boolean;
  enableDocSelection?: boolean;
  isMainDocument?: boolean;
  selectionReadOnly?: boolean;
  isContextMemoryParameter?: boolean;
  isAdditionalContext?: boolean;
  selectedDocumentIds?: string[];
  selectedDocumentId?: string;
  [key: string]: any;
}

export interface CpInteraction {
  id: string;
  name?: string;
  endpoint?: string;
  description?: string;
  model?: string;
  prompt?: string;
  status?: string;
  version?: string;
  parameters?: CpInteractionParameter[];
  activeOnDocumentTypes?: string[];
  isActive?: boolean;
  isChat?: boolean;
  /** Java's boolean bean accessors serialize these flags without an `is` prefix. */
  active?: boolean;
  chat?: boolean;
  activeOnFolder?: boolean;
  activeOnActionMenu?: boolean;
  saveMetadata?: boolean;
  mainMetadata?: boolean;
  sendParentsContext?: boolean;
  sendAttachments?: boolean;
  requiresUserInput?: boolean;
  [key: string]: any;
}

export interface CpEventSettings { [key: string]: any; }
export interface CpAiSettings { [key: string]: any; }
export interface CpEsSettings { [key: string]: string; }
export interface CpSettings {
  interactions: CpInteraction[];
  eventsSettings: CpEventSettings[];
  aiSettings: CpAiSettings;
  esSettings: CpEsSettings;
  schemaSettings?: Record<string, any>;
  awsS3AccessEnabled: boolean;
  [key: string]: any;
}

/** Automation JSONBlob responses wrap their application payload in `value`. */
export function unwrapCpResponse<T>(response: unknown): T {
  return response !== null && typeof response === 'object' &&
    Object.prototype.hasOwnProperty.call(response, 'value')
    ? (response as { value: T }).value
    : response as T;
}

export function cpHasGroup(user: { isAdministrator?: boolean; groups?: readonly string[] } | null | undefined, group: string): boolean {
  return user?.isAdministrator === true || user?.groups?.includes('administrators') === true ||
    user?.groups?.includes(group) === true;
}
