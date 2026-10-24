import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DocumentActionContext } from '@maretha/webui-sdk';
import { CpApiService } from '../../shared/services/cp-api.service';
import { CpDocument } from '../../shared/types/cp-models';
import type { CpInteraction } from '../../shared/types/interaction-models';
import { errorMessage, hasGroup, isBulkDocument } from '../../shared/utils/interaction-utils';

@Component({
  selector: 'cp-metadata-action', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (allowed() && interaction()) {
    <details #menu><summary [attr.aria-disabled]="pending()" title="AI Metadata">✧ <span>AI Metadata</span></summary><div class="menu"><button type="button" [disabled]="pending()" (click)="run(true); menu.open = false">Update metadata</button><button type="button" [disabled]="pending()" (click)="run(false); menu.open = false">Enhance metadata</button></div></details>
  }
  @if (pending()) { <span role="status">Metadata request in progress…</span> }
  @if (notice()) { <span role="status">{{ notice() }}</span> }
  @if (error()) { <span role="alert">{{ error() }}</span> }`,
  styles: `:host{display:inline-flex;align-items:center;gap:8px;position:relative;font-size:13px}summary{cursor:pointer;list-style:none;display:flex;align-items:center;gap:6px;padding:9px;color:var(--cp-primary,#3972b9)}summary::-webkit-details-marker{display:none}.menu{position:absolute;right:0;top:100%;z-index:30;min-width:190px;padding:6px;background:var(--cp-surface,#fff);border:1px solid var(--cp-border,#ddd);box-shadow:0 3px 12px #0002}.menu button{display:block;width:100%;font:inherit;text-align:left;padding:10px;border:0;background:transparent;color:inherit;cursor:pointer}.menu button:hover{background:var(--cp-subtle,#edf2f5)}button:disabled{opacity:.5;cursor:default}[role=alert]{color:#b3261e}summary:focus-visible,button:focus-visible{outline:2px solid var(--cp-primary,#3972b9);outline-offset:2px}`,
})
export class CpMetadataActionComponent {
  readonly context = input.required<DocumentActionContext>();
  readonly sourceInput = input<CpDocument | null>(null);
  readonly pending = signal(false);
  readonly notice = signal('');
  readonly error = signal('');
  readonly interaction = signal<CpInteraction | null>(null);
  private readonly api = inject(CpApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly allowed = computed(() => !this.context().noEdit && hasGroup(this.context().user ?? this.api.user(), 'AI-METADATA'));
  private generation = 0;
  constructor() {
    effect(onCleanup => {
      const doc = this.sourceInput() ?? this.context().document as CpDocument | null;
      const allowed = this.allowed();
      this.generation++; this.interaction.set(null); this.pending.set(false); this.error.set(''); this.notice.set('');
      if (!doc?.uid || !allowed) return;
      const subscription = this.api.execute<CpInteraction[]>('GetInteractionsForDocumentOperation', { isMetadata: true }, doc).subscribe({
        next: interactions => this.interaction.set(interactions.find(interaction => interaction.saveMetadata === true) ?? null),
        error: error => this.error.set(errorMessage(error)),
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
  run(override: boolean): void {
    const interaction = this.interaction(), doc = this.sourceInput() ?? this.context().document as CpDocument | null;
    if (!doc || !interaction || !this.allowed() || this.pending()) return;
    const generation = this.generation;
    this.pending.set(true); this.error.set(''); this.notice.set('');
    this.api.execute<unknown>('RunInteractionOperation', { interactionId: interaction.id, data: interaction.parameters ?? [], saveMetadata: true, saveMetadataModeOverride: override }, doc.uid).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        if (generation !== this.generation) return;
        this.pending.set(false);
        this.notice.set(isBulkDocument(doc) ? `Metadata job submitted: ${String(response)}` : override ? 'Metadata updated.' : 'Metadata enhanced.');
        this.context().refreshDocument?.();
      },
      error: error => { if (generation !== this.generation) return; this.pending.set(false); this.error.set(errorMessage(error)); },
    });
  }
}
