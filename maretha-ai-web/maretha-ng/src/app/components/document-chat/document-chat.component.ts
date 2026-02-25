import { Component, Input, OnInit, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NuxeoService } from '../../nuxeo.service';

interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
    date: string;
    param?: string;
}

interface InteractionParam {
    name: string;
    type: string;
    value: string;
    readOnly?: boolean;
    isContextMemoryParameter?: boolean;
}

interface ChatInteraction {
    id: string;
    name: string;
    description: string;
    parameters: InteractionParam[];
}

@Component({
    selector: 'maretha-document-chat',
    standalone: true,
    imports: [CommonModule, FormsModule],
    encapsulation: ViewEncapsulation.ShadowDom,
    template: `
    <div class="chat-wrapper">
      <!-- Chat history -->
      <div class="chat-response" #chatContainer>
        <div *ngFor="let msg of chatContext" class="message" [class.user]="msg.role === 'user'" [class.assistant]="msg.role === 'assistant'">
          <span class="role">{{ msg.role === 'user' ? '🧑' : '🤖' }}</span>
          <div class="content" [innerHTML]="msg.content"></div>
        </div>
        <div *ngIf="loading" class="loading-indicator">
          <span>⏳ Waiting for AI response...</span>
        </div>
      </div>

      <!-- Input area -->
      <div class="question" *ngIf="chatInteraction">
        <div class="inputs">
          <ng-container *ngFor="let param of chatInteraction.parameters">
            <input
              *ngIf="!param.readOnly && !param.isContextMemoryParameter"
              class="chat-input"
              [(ngModel)]="param.value"
              [placeholder]="param.name"
              [type]="param.type === 'string' ? 'text' : param.type"
              (keydown.enter)="interactWithDocument()"
            />
          </ng-container>
        </div>
        <button class="send-btn" (click)="interactWithDocument()" [disabled]="loading" title="Ask question">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M2 21l21-9L2 3v7l15 2-15 2v7z"/></svg>
        </button>
      </div>

      <div *ngIf="!chatInteraction && !loading" class="empty-state">
        No chat interaction configured for this document type.
      </div>
    </div>
  `,
    styles: [`
    :host { display: block; font-family: inherit; }
    .chat-wrapper {
      display: flex; flex-direction: column;
      border: 1px solid rgba(58,85,17,.3);
      box-shadow: 0 0 3.5rem -2rem inset rgba(58,85,17,.5);
      padding: 1rem; gap: 1rem; overflow: hidden;
      max-height: 500px; border-radius: .325rem; margin: .5rem 0;
    }
    .chat-response { flex-grow: 1; overflow-y: auto; display: flex; flex-direction: column; gap: .75rem; }
    .message { display: flex; gap: .5rem; align-items: flex-start; }
    .message.user .content { background: #e8f4e8; border-radius: .5rem; padding: .5rem .75rem; }
    .message.assistant .content { background: #f0f4ff; border-radius: .5rem; padding: .5rem .75rem; }
    .role { font-size: 1.2rem; flex-shrink: 0; }
    .content { font-size: .9rem; line-height: 1.5; white-space: pre-wrap; }
    .question { display: flex; align-items: center; gap: .75rem; padding-top: .5rem; border-top: 1px solid #eee; }
    .inputs { flex-grow: 1; display: flex; flex-wrap: wrap; gap: .5rem; }
    .chat-input {
      flex: 1; min-width: 150px; padding: .5rem .75rem;
      border: 1px solid #ccc; border-radius: .25rem;
      font-size: .9rem; outline: none;
    }
    .chat-input:focus { border-color: var(--nuxeo-button-primary, #5cb85c); }
    .send-btn {
      background: var(--nuxeo-button-primary, #5cb85c);
      color: white; border: none; border-radius: .25rem;
      padding: .5rem; cursor: pointer; display: flex; align-items: center;
    }
    .send-btn:disabled { opacity: .5; cursor: not-allowed; }
    .loading-indicator { color: #888; font-style: italic; font-size: .9rem; }
    .empty-state { color: #888; text-align: center; padding: 1rem; font-size: .9rem; }
  `]
})
export class MarethaDocumentChatComponent implements OnInit, OnChanges {
    @Input('document-id') documentId: string = '';
    @Input() document: any;

    chatContext: ChatMessage[] = [];
    chatInteraction: ChatInteraction | null = null;
    loading = false;

    constructor(private nuxeo: NuxeoService) { }

    ngOnInit() {
        this.loadChatInteraction();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes['document'] || changes['documentId']) {
            this.chatContext = [];
            this.loadChatInteraction();
        }
    }

    loadChatInteraction() {
        const docInput = this.document?.uid || this.documentId;
        if (!docInput) return;

        this.nuxeo.executeOperation('GetInteractionsForDocumentOperation', { isChat: true }, docInput)
            .then((response: any) => {
                if (response.value?.length > 0) {
                    this.chatInteraction = response.value[0];
                }
            })
            .catch((err: any) => console.error('[MarethaChat] Failed to load interaction:', err));
    }

    interactWithDocument() {
        if (!this.chatInteraction || this.loading) return;

        // Add user messages to context
        this.chatInteraction.parameters.forEach(param => {
            if (!param.isContextMemoryParameter && param.type === 'string' && param.value) {
                this.chatContext.push({ role: 'user', content: param.value, date: new Date().toISOString(), param: param.name });
            }
        });

        const placeholderIndex = this.chatContext.length;
        this.chatContext.push({ role: 'assistant', content: 'Waiting for AI response...', date: new Date().toISOString() });
        this.loading = true;

        const docInput = this.document?.uid || this.documentId;
        this.nuxeo.executeOperation('RunInteractionOperation', {
            interactionId: this.chatInteraction.id,
            data: this.chatInteraction.parameters
        }, docInput)
            .then((response: any) => {
                this.chatContext[placeholderIndex].content = response.value;
                this.loading = false;
            })
            .catch((err: any) => {
                this.chatContext[placeholderIndex].content = 'Error: ' + err.message;
                this.loading = false;
            });
    }
}
