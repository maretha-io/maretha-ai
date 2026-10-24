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
