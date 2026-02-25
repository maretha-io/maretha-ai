import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NuxeoService } from '../../nuxeo.service';

interface InteractionParam {
    name: string;
    type: string;
    value: string;
    readOnly?: boolean;
    enableDocSelection?: boolean;
    selectedDocumentIds?: string[];
    selectionReadOnly?: boolean;
    isContextMemoryParameter?: boolean;
    format?: string;
}

interface Interaction {
    id: string;
    name: string;
    description: string;
    parameters: InteractionParam[];
    saveMetadata?: boolean;
}

interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
    date: string;
}

@Component({
    selector: 'maretha-document-interaction',
    standalone: true,
    imports: [CommonModule, FormsModule],
    encapsulation: ViewEncapsulation.ShadowDom,
    template: `
    <div class="interaction-wrapper">
      <!-- Response display -->
      <div class="chat-response">
        <div *ngFor="let msg of interactionContext" class="message" [class.user]="msg.role === 'user'" [class.assistant]="msg.role === 'assistant'">
          <span class="role">{{ msg.role === 'user' ? '🧑' : '🤖' }}</span>
          <div class="content" [innerHTML]="msg.content"></div>
        </div>
        <div *ngIf="loading" class="loading-indicator">⏳ Running interaction...</div>
      </div>

      <!-- Controls -->
      <div class="controls">
        <select class="interaction-select" [(ngModel)]="selectedIndex" (change)="onInteractionSelected()">
          <option value="-1" disabled>Select AI Interaction</option>
          <option *ngFor="let i of interactions; let idx = index" [value]="idx">{{ i.name }}</option>
        </select>

        <div class="params" *ngIf="selectedInteraction">
          <div *ngFor="let param of selectedInteraction.parameters" class="param-row">
            <label class="param-label">{{ param.name }}</label>
            <input
              *ngIf="!param.readOnly && !param.enableDocSelection"
              class="param-input"
              [(ngModel)]="param.value"
              [type]="param.type === 'string' ? 'text' : param.type"
              [placeholder]="param.name"
            />
            <input
              *ngIf="param.enableDocSelection"
              class="param-input"
              [(ngModel)]="param.value"
              placeholder="Document path or UID"
            />
          </div>
        </div>

        <button class="run-btn" (click)="interactWithDocument()" [disabled]="loading || !selectedInteraction" title="Run interaction">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8 5v14l11-7z"/></svg>
        </button>
      </div>
    </div>
  `,
    styles: [`
    :host { display: block; font-family: inherit; }
    .interaction-wrapper {
      display: flex; flex-direction: column;
      border: 1px solid rgba(58,85,17,.3);
      box-shadow: 0 0 3.5rem -2rem inset rgba(58,85,17,.5);
      padding: 1rem; gap: 1rem; max-height: 500px;
      border-radius: .325rem; margin: .5rem 0;
    }
    .chat-response { flex-grow: 1; overflow-y: auto; display: flex; flex-direction: column; gap: .5rem; }
    .message { display: flex; gap: .5rem; }
    .message.user .content { background: #e8f4e8; border-radius: .5rem; padding: .5rem; }
    .message.assistant .content { background: #f0f4ff; border-radius: .5rem; padding: .5rem; }
    .content { font-size: .9rem; white-space: pre-wrap; }
    .controls { display: flex; flex-direction: column; gap: .75rem; border-top: 1px solid #eee; padding-top: .75rem; }
    .interaction-select { padding: .5rem; border: 1px solid #ccc; border-radius: .25rem; font-size: .9rem; }
    .params { display: flex; flex-direction: column; gap: .5rem; }
    .param-row { display: flex; align-items: center; gap: .75rem; }
    .param-label { font-weight: 500; min-width: 120px; font-size: .85rem; color: var(--nuxeo-button-primary, #5cb85c); }
    .param-input { flex: 1; padding: .5rem; border: 1px solid #ccc; border-radius: .25rem; font-size: .9rem; }
    .run-btn {
      align-self: flex-end;
      background: var(--nuxeo-button-primary, #5cb85c);
      color: white; border: none; border-radius: .25rem;
      padding: .5rem .75rem; cursor: pointer; display: flex; align-items: center; gap: .25rem;
    }
    .run-btn:disabled { opacity: .5; }
    .loading-indicator { color: #888; font-style: italic; font-size: .9rem; }
  `]
})
export class MarethaDocumentInteractionComponent implements OnChanges {
    @Input('document-id') documentId: string = '';
    @Input() document: any;

    interactions: Interaction[] = [];
    selectedIndex = -1;
    selectedInteraction: Interaction | null = null;
    interactionContext: ChatMessage[] = [];
    loading = false;

    constructor(private nuxeo: NuxeoService) { }

    ngOnChanges(changes: SimpleChanges) {
        if (changes['document'] || changes['documentId']) {
            this.reset();
            this.loadInteractions();
        }
    }

    reset() {
        this.interactions = [];
        this.selectedIndex = -1;
        this.selectedInteraction = null;
        this.interactionContext = [];
    }

    loadInteractions() {
        const docInput = this.document?.uid || this.documentId;
        if (!docInput) return;

        this.nuxeo.executeOperation('GetInteractionsForDocumentOperation', {}, docInput)
            .then((response: any) => {
                this.interactions = response.value || [];
                if (this.interactions.length > 0) {
                    this.selectedIndex = 0;
                    this.selectedInteraction = this.interactions[0];
                }
            })
            .catch((err: any) => console.error('[MarethaInteraction] Failed to load interactions:', err));
    }

    onInteractionSelected() {
        const idx = Number(this.selectedIndex);
        this.selectedInteraction = idx >= 0 ? this.interactions[idx] : null;
        this.interactionContext = [];
    }

    interactWithDocument() {
        if (!this.selectedInteraction || this.loading) return;

        this.interactionContext = [
            { role: 'user', content: this.selectedInteraction.description, date: new Date().toISOString() },
            { role: 'assistant', content: 'Waiting for AI response...', date: new Date().toISOString() }
        ];
        const placeholderIndex = 1;
        this.loading = true;

        const docInput = this.document?.uid || this.documentId;
        this.nuxeo.executeOperation('RunInteractionOperation', {
            interactionId: this.selectedInteraction.id,
            data: this.selectedInteraction.parameters,
            saveMetadata: this.selectedInteraction.saveMetadata
        }, docInput)
            .then((response: any) => {
                this.interactionContext[placeholderIndex].content = response.value;
                this.loading = false;
            })
            .catch((err: any) => {
                this.interactionContext[placeholderIndex].content = 'Error: ' + err.message;
                this.loading = false;
            });
    }
}
