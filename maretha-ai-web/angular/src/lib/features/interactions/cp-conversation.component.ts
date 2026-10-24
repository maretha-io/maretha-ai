import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CpApiService } from '../../shared/services/cp-api.service';
import { CpDocument } from '../../shared/types/cp-models';
import { CpParametersComponent } from './cp-parameters.component';
import { CpResponseComponent } from './cp-response.component';
import { CpInteractionIconComponent } from './cp-interaction-icon.component';
import type { CpInteraction, CpMessage } from '../../shared/types/interaction-models';
import { errorMessage, isBulkDocument } from '../../shared/utils/interaction-utils';

@Component({
  selector: 'cp-conversation', standalone: true, imports: [DatePipe, CpParametersComponent, CpResponseComponent, CpInteractionIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="conversation" [attr.aria-busy]="pending()">
    @if (messages().length) { <div class="messages" aria-live="polite">
      @for (message of messages(); track $index) {
        <article [class.assistant]="message.role === 'assistant'" [class.failed]="message.failed"><header>{{ message.role === 'user' ? 'You' : 'AI' }} <time>{{ message.date | date:'HH:mm' }}</time></header>
          <cp-response [value]="message.content" />
          @if (message.role === 'assistant' && !message.pending && !message.failed && message.interaction) {
            <details class="save-menu"><summary aria-label="Save response" title="Save response">⋮</summary><div class="save-options"><button type="button" [disabled]="saving()" (click)="save(message, true)">Save interaction as metadata</button><button type="button" [disabled]="saving()" (click)="save(message, false)">Save interaction as new document</button></div></details>
          }
        </article>
      }
    </div> }
    @if (error()) { <p role="alert" class="error">{{ error() }}</p> }
    @if (notice()) { <p role="status">{{ notice() }}</p> }
    @if (interaction(); as selected) {
      <div class="controls"><div class="parameter-fields"><ng-content />@if (parameters().length) { <cp-parameters [parameters]="parameters()" [prefix]="chatMode() ? 'chat-' : 'run-'" [compact]="chatMode()" [disabled]="pending()" (submit)="run()" /> }</div>
        <button class="run-button" type="button" [disabled]="pending()" [attr.aria-label]="pending() ? 'Waiting for AI response' : chatMode() ? 'Ask question' : 'Run interaction'" [title]="chatMode() ? 'Ask question' : 'Run interaction'" (click)="run()"><cp-interaction-icon [chat]="chatMode()" /></button>
      </div>
      @if (messages().length) { <footer><button class="reset" type="button" [disabled]="pending() || saving()" (click)="reset()">Reset</button></footer> }
    }
  </section>`,
  styleUrl: './interactions.css',
})
export class CpConversationComponent {
  readonly document = input.required<CpDocument>();
  readonly interaction = input<CpInteraction | null>(null);
  readonly chatMode = input(false);
  readonly saved = output<void>();
  readonly messages = signal<CpMessage[]>([]);
  readonly parameters = signal<CpInteraction['parameters']>([]);
  readonly pending = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  private readonly api = inject(CpApiService);
  private readonly destroyRef = inject(DestroyRef);
  private generation = 0;
  private sourceKey = '';
  constructor() { effect(() => {
    const doc = this.document(), interaction = this.interaction();
    const key = `${doc.uid}:${interaction?.id ?? ''}`;
    if (key === this.sourceKey) return;
    this.sourceKey = key; this.reset();
  }); }
  reset(): void { this.generation++; this.parameters.set(structuredClone(this.interaction()?.parameters ?? [])); this.messages.set([]); this.pending.set(false); this.saving.set(false); this.error.set(''); this.notice.set(''); }
  run(): void {
    const interaction = this.interaction();
    if (!interaction || this.pending()) return;
    const doc = this.document(), generation = this.generation;
    const parameters = structuredClone(this.parameters());
    const now = new Date().toISOString();
    const userMessages: CpMessage[] = this.chatMode() ? parameters.filter(p => !p.isContextMemoryParameter && p.type === 'string' && p.value != null && String(p.value).trim()).map(p => ({ role: 'user', param: p.name, content: String(p.value), date: now })) : [{ role: 'user', content: interaction.description || interaction.name, date: now }];
    if (this.chatMode() && !userMessages.length) { this.error.set('Enter a question before sending.'); return; }
    const context = [...(this.chatMode() ? this.messages().filter(message => !message.pending && !message.failed) : []), ...userMessages];
    for (const parameter of parameters) if (parameter.isContextMemoryParameter) parameter.value = JSON.stringify(context.map(({ role, content, date, param }) => ({ role, content, date, ...(param ? { param } : {}) })));
    const snapshot = { ...structuredClone(interaction), parameters };
    this.messages.set([...context, { role: 'assistant', date: now, content: 'Waiting for AI response…', pending: true }]);
    this.pending.set(true); this.error.set(''); this.notice.set('');
    this.api.execute<unknown>('RunInteractionOperation', { interactionId: interaction.id, data: parameters, saveMetadata: !!interaction.saveMetadata }, doc.uid).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        if (generation !== this.generation) return;
        const bulk = !!interaction.saveMetadata && isBulkDocument(doc);
        const content = bulk ? `Metadata job submitted: ${String(response)}` : typeof response === 'string' ? response : JSON.stringify(response, null, 2);
        this.messages.set([...context, { role: 'assistant', date: new Date().toISOString(), content, ...(!bulk ? { interaction: snapshot } : {}) }]);
        this.pending.set(false);
        if (interaction.saveMetadata) this.saved.emit();
      },
      error: error => {
        if (generation !== this.generation) return;
        const content = errorMessage(error);
        this.messages.set([...context, { role: 'assistant', date: new Date().toISOString(), content, failed: true }]);
        this.error.set(content); this.pending.set(false);
      },
    });
  }
  save(message: CpMessage, saveOnMetadata: boolean): void {
    const interaction = message.interaction;
    if (!interaction || this.saving() || message.pending || message.failed) return;
    const generation = this.generation, doc = this.document();
    this.saving.set(true); this.error.set(''); this.notice.set('');
    this.api.execute<CpDocument>('SaveInteractionResponseOperation', { saveOnMetadata, interactions: [{
      id: `${doc.uid}-${interaction.id}-${new Date().toISOString()}`, interactionId: interaction.id, documentId: doc.uid,
      response: message.content, name: interaction.name, status: interaction.status, createdAt: new Date().toISOString(), createdBy: this.api.user()?.id ?? '', parameters: interaction.parameters,
    }] }, doc).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { if (generation !== this.generation) return; this.saving.set(false); this.notice.set('Response saved.'); this.saved.emit(); },
      error: error => { if (generation !== this.generation) return; this.error.set(errorMessage(error)); this.saving.set(false); },
    });
  }
}
