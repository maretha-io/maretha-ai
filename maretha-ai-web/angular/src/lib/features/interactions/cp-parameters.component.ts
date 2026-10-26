import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CpApiService } from '../../shared/services/cp-api.service';
import { CpDocument } from '../../shared/types/cp-models';
import type { CpParameter } from '../../shared/types/interaction-models';
import { cpParameterVisible, errorMessage } from '../../shared/utils/interaction-utils';
import { parameterDocumentTitles } from '../../shared/utils/parameter-documents';

@Component({
  selector: 'cp-parameters', standalone: true, imports: [FormsModule], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@for (param of parameters(); track $index; let index = $index) {
    @if (visible(param, compact())) {
      <div class="parameter"><label [for]="'cp-param-' + prefix() + index">{{ param.name }}</label>
        @if (param.description) { <small>{{ param.description }}</small> }
        @if (param.type === 'boolean') {
          <select [id]="'cp-param-' + prefix() + index" [disabled]="disabled() || !!param.readOnly" [ngModel]="param.value" (ngModelChange)="setValue(param, $event)"><option value="true">True</option><option value="false">False</option></select>
        } @else if (compact() || param.type !== 'array' || param.format !== 'document') {
          <input [id]="'cp-param-' + prefix() + index" [placeholder]="compact() ? param.name : ''" [type]="param.type === 'number' || param.type === 'integer' ? 'number' : 'text'" [readonly]="!!param.readOnly" [disabled]="disabled()" [ngModel]="param.value ?? ''" (ngModelChange)="setValue(param, $event)" (keydown)="keyPressed($event)" />
        }
        @if (!compact() && (param.enableDocSelection || (param.type === 'array' && param.format === 'document'))) {
          <div class="documents" aria-label="Selected documents">
            @for (uid of param.selectedDocumentIds ?? []; track uid) { <span class="chip">{{ titles()[uid] || uid }} @if (!param.selectionReadOnly && !disabled()) { <button type="button" [attr.aria-label]="'Remove ' + (titles()[uid] || uid)" (click)="remove(param, uid)">×</button> }</span> }
          </div>
          @if (!param.selectionReadOnly) {
            <input [attr.aria-label]="'Find documents for ' + param.name" placeholder="Find documents by title" [disabled]="disabled()" (input)="search(param, $any($event.target).value)" />
            @if (active() === param) {
              @if (searching()) { <small role="status">Finding documents…</small> }
              @for (doc of suggestions(); track doc.uid) { <button class="suggestion" type="button" (click)="select(param, doc)">{{ doc.title || doc.properties['dc:title'] || doc.uid }}</button> }
              @if (searchError()) { <small role="alert">{{ searchError() }}</small> }
            }
          }
        }
      </div>
    }
  }`,
  host: { '[class.compact]': 'compact()' },
  styles: `:host{display:grid;gap:16px}.parameter{display:grid;gap:6px}label{font-weight:500}small{color:var(--cp-muted,#888)}input,select{font:inherit;padding:5px 0;border:0;border-bottom:1px solid var(--cp-text,#454552);border-radius:0;background:transparent;color:inherit;min-width:0;width:100%}input::placeholder{color:#999}:host.compact{display:flex;gap:12px;flex-wrap:wrap}:host.compact .parameter{flex:1 0 calc(50% - 12px);min-width:120px}:host.compact label,:host.compact small{display:none}.documents{display:flex;gap:6px;flex-wrap:wrap}.chip{display:inline-flex;align-items:center;gap:8px;background:var(--cp-subtle,#f1f4f6);padding:5px 8px;border-radius:3px}.chip button{border:0;background:none;cursor:pointer}.suggestion{text-align:left;padding:8px;border:1px solid var(--cp-border,#ddd);background:var(--cp-surface,#fff);color:inherit;cursor:pointer}`,
})
export class CpParametersComponent {
  readonly visible = cpParameterVisible;
  readonly parameters = input<CpParameter[]>([]);
  readonly disabled = input(false);
  readonly prefix = input('');
  readonly compact = input(false);
  readonly submit = output<void>();
  readonly changed = output<void>();
  readonly active = signal<CpParameter | null>(null);
  readonly suggestions = signal<CpDocument[]>([]);
  readonly titles = signal<Record<string, string>>({});
  readonly searching = signal(false);
  readonly searchError = signal('');
  private readonly api = inject(CpApiService);
  private readonly destroyRef = inject(DestroyRef);
  private request = 0;
  constructor() {
    effect(onCleanup => {
      const parameters = this.parameters();
      this.titles.set({});
      const subscription = parameterDocumentTitles(parameters, uid => this.api.getDocument(uid)).subscribe(titles => this.titles.set(titles));
      onCleanup(() => subscription.unsubscribe());
    });
  }
  setValue(param: CpParameter, value: unknown): void { param.value = value == null ? '' : String(value); this.changed.emit(); }
  keyPressed(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.isComposing) { event.preventDefault(); this.submit.emit(); }
  }
  search(param: CpParameter, text: string): void {
    const request = ++this.request;
    this.active.set(param); this.suggestions.set([]); this.searchError.set('');
    if (text.trim().length < 2) { this.searching.set(false); return; }
    this.searching.set(true);
    const escaped = text.trim().replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/[%_]/g, '');
    this.api.query(`SELECT * FROM Document WHERE ecm:isTrashed = 0 AND ecm:isProxy = 0 AND dc:title ILIKE '%${escaped}%' ORDER BY dc:title`).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { if (request !== this.request) return; this.suggestions.set((result.entries ?? []).slice(0, 20)); this.searching.set(false); },
      error: error => { if (request !== this.request) return; this.searchError.set(errorMessage(error)); this.searching.set(false); },
    });
  }
  select(param: CpParameter, doc: CpDocument): void {
    if (param.selectionReadOnly || this.disabled()) return;
    param.selectedDocumentIds = [...new Set([...(param.selectedDocumentIds ?? []), doc.uid])];
    this.titles.update(titles => ({ ...titles, [doc.uid]: doc.title ?? doc.properties['dc:title'] ?? doc.uid }));
    this.suggestions.set([]); this.changed.emit();
  }
  remove(param: CpParameter, uid: string): void {
    if (param.selectionReadOnly || this.disabled()) return;
    param.selectedDocumentIds = param.selectedDocumentIds?.filter(id => id !== uid); this.changed.emit();
  }
}
