import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CpApiService } from '../../shared/services/cp-api.service';
import { CpDocument } from '../../shared/types/cp-models';
import { CpResponseComponent } from './cp-response.component';
import type { CpSavedInteraction } from '../../shared/types/interaction-models';
import { errorMessage, mergeSavedInteractions } from '../../shared/utils/interaction-utils';

@Component({
  selector: 'cp-results', standalone: true, imports: [DatePipe, RouterLink, CpResponseComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) { <p role="status">Loading saved interactions…</p> }
    @if (error()) { <p role="alert">{{ error() }} <button type="button" (click)="reload()">Retry</button></p> }
    @for (result of results(); track $index) {
      <article><header><h3>{{ result.name }}</h3><time>{{ dateValue(result.createdAt) | date:'yyyy-MM-dd HH:mm:ss' }}</time></header>
        <cp-response [value]="result.response" />
        @if (result.doc) { <a [routerLink]="['/browse/documents', result.doc.uid]">{{ result.doc.title || 'Open saved interaction' }} ↗</a> }
      </article>
    } @empty { @if (!loading() && !error()) { <p class="empty">No saved interactions.</p> } }
  `,
  styles: `:host{display:block}article{padding:20px 0}article+article{border-top:1px solid var(--cp-border,#ddd)}header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}h3{font-size:14px;margin:0}time,.empty{color:var(--cp-muted,#777);font-size:13px}a{display:inline-block;margin-top:16px;color:var(--cp-primary,#3972b9)}button{font:inherit}`,
})
export class CpResultsComponent {
  readonly document = input.required<CpDocument>();
  readonly sourceInput = input<CpDocument | null>(null);
  readonly refreshToken = input(0);
  readonly includeRelated = input(true);
  readonly results = signal<CpSavedInteraction[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  private readonly api = inject(CpApiService);
  private readonly revision = signal(0);

  constructor() {
    effect(onCleanup => {
      this.refreshToken(); this.revision();
      const doc = this.sourceInput() ?? this.document();
      this.results.set([]); this.error.set(''); this.loading.set(true);
      const source = doc.properties?.['interactionresults:interactions'];
      this.results.set(mergeSavedInteractions(Array.isArray(source) ? source : [], []));
      if (!this.includeRelated()) { this.loading.set(false); return; }
      const subscription = this.api.execute<{ entries?: CpDocument[] } | CpDocument[]>('Relations.GetRelations', {
        outgoing: true, predicate: 'http://www.nuxeo.org/ecm/schemas/relation#isRelatedTo',
      }, doc).subscribe({
        next: related => {
          this.results.set(mergeSavedInteractions(Array.isArray(source) ? source : [], Array.isArray(related) ? related : related?.entries ?? []));
          this.loading.set(false);
        },
        error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
  reload(): void { this.revision.update(value => value + 1); }
  dateValue(value: string): Date | null { const timestamp = Date.parse(value); return Number.isFinite(timestamp) ? new Date(timestamp) : null; }
}
