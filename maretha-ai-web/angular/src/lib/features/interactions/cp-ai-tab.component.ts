import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DocumentActionContext } from '@maretha/webui-sdk';
import { Subscription } from 'rxjs';
import { CpApiService } from '../../shared/services/cp-api.service';
import { CpDocument } from '../../shared/types/cp-models';
import { CpConversationComponent } from './cp-conversation.component';
import { CpResultsComponent } from './cp-results.component';
import type { CpInteraction } from '../../shared/types/interaction-models';
import { errorMessage, hasGroup } from '../../shared/utils/interaction-utils';

@Component({
  selector: 'cp-ai-tab', standalone: true, imports: [FormsModule, CpConversationComponent, CpResultsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (currentDocument(); as doc) {
    @if (canChat()) {
      <details><summary><span>{{ chat()?.name || 'Chat with document' }}</span><small>{{ chat()?.description || 'Ask questions about this document' }}</small></summary>
        <div class="body">
          @if (chatLoading()) { <p role="status">Loading chat interaction…</p> }
          @if (chatError()) { <p role="alert">{{ chatError() }}</p> }
          @if (chat(); as interaction) { <cp-conversation [document]="doc" [interaction]="interaction" [chatMode]="true" (saved)="refresh()" /> }
          @if (!chat() && !chatLoading() && !chatError()) { <p>No chat interaction is configured for this document type.</p> }
        </div>
      </details>
    }
    @if (canRun()) {
      <details><summary><span>Run interactions</span><small>Execute different interactions against this document</small></summary>
        <div class="body">
          @if (runLoading()) { <p role="status">Loading interactions…</p> }
          @if (runError()) { <p role="alert">{{ runError() }}</p> }
          @if (interactions().length) {
            <cp-conversation [document]="doc" [interaction]="selected()" (saved)="refresh()"><div class="interaction-picker"><label class="select-label" for="cp-selected-interaction">Select AI Interaction</label>
              <select id="cp-selected-interaction" [ngModel]="selectedId()" (ngModelChange)="selectedId.set($event)">@for (interaction of interactions(); track interaction.id) { <option [value]="interaction.id">{{ interaction.name }}</option> }</select></div>
            </cp-conversation>
          } @else if (!runLoading() && !runError()) { <p>No interactions are configured for this document type.</p> }
        </div>
      </details>
    }
    @if (hasResults()) {
      <details><summary><span>Saved interactions</span><small>Your previously saved interactions</small></summary><div class="body"><cp-results [document]="doc" [refreshToken]="refreshToken()" /></div></details>
    }
    @if (!canChat() && !canRun() && !hasResults()) { <p>No AI interactions are available for your account.</p> }
  }`,
  styleUrl: './ai-tab.css',
})
export class CpAiTabComponent {
  readonly context = input.required<DocumentActionContext>();
  readonly sourceInput = input<CpDocument | null>(null);
  readonly api = inject(CpApiService);
  private readonly refreshedDocument = signal<CpDocument | null>(null);
  readonly currentDocument = computed(() => this.refreshedDocument() ?? this.sourceInput() ?? this.context().document as CpDocument | null);
  readonly canChat = computed(() => hasGroup(this.context().user ?? this.api.user(), 'AI-CHAT'));
  readonly canRun = computed(() => hasGroup(this.context().user ?? this.api.user(), 'AI-INTERACTION'));
  readonly chat = signal<CpInteraction | null>(null);
  readonly interactions = signal<CpInteraction[]>([]);
  readonly selectedId = signal('');
  readonly selected = computed(() => this.interactions().find(interaction => interaction.id === this.selectedId()) ?? null);
  readonly chatLoading = signal(false);
  readonly runLoading = signal(false);
  readonly chatError = signal('');
  readonly runError = signal('');
  readonly refreshToken = signal(0);
  readonly hasResults = computed(() => !!this.currentDocument()?.facets?.includes('InteractionResultsFacet') || Array.isArray(this.currentDocument()?.properties?.['interactionresults:interactions']));
  private generation = 0;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(onCleanup => {
      const document = this.sourceInput() ?? this.context().document as CpDocument | null;
      const canChat = this.canChat(), canRun = this.canRun();
      this.generation++; this.refreshedDocument.set(null);
      this.chat.set(null); this.interactions.set([]); this.selectedId.set('');
      this.chatError.set(''); this.runError.set('');
      this.chatLoading.set(!!document && canChat); this.runLoading.set(!!document && canRun);
      if (!document?.uid) return;
      const subscriptions: Subscription[] = [];
      if (canChat) subscriptions.push(this.api.execute<CpInteraction[]>('GetInteractionsForDocumentOperation', { isChat: true }, document).subscribe({
        next: interactions => { this.chat.set(interactions?.[0] ?? null); this.chatLoading.set(false); },
        error: error => { this.chatError.set(errorMessage(error)); this.chatLoading.set(false); },
      }));
      if (canRun) subscriptions.push(this.api.execute<CpInteraction[]>('GetInteractionsForDocumentOperation', {}, document).subscribe({
        next: interactions => { this.interactions.set(interactions ?? []); this.selectedId.set(interactions?.[0]?.id ?? ''); this.runLoading.set(false); },
        error: error => { this.runError.set(errorMessage(error)); this.runLoading.set(false); },
      }));
      onCleanup(() => subscriptions.forEach(subscription => subscription.unsubscribe()));
    });
  }
  refresh(): void {
    const doc = this.currentDocument();
    if (!doc) return;
    this.context().refreshDocument?.();
    this.refreshToken.update(value => value + 1);
    const generation = this.generation;
    this.api.getDocument(doc.uid).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: fresh => {
      if (generation !== this.generation) return;
      this.refreshedDocument.set(fresh); this.refreshToken.update(value => value + 1);
    }, error: () => { /* Host refresh still runs; existing response remains visible. */ } });
  }
}
