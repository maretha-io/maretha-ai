import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { CpApiService } from '../cp-api.service';
import { nxqlSearchLiteral } from './settings-state';

interface PickerDocument { uid: string; title?: string; properties?: Record<string, unknown>; }
@Component({
  selector: 'cp-settings-document-picker', standalone: true, imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset>
      <legend>{{ label() }}</legend>
      <ul class="selected">
        @for (id of values(); track id) {
          <li>{{ titles()[id] || id }}
            <button type="button" (click)="remove(id)" [disabled]="disabled()"
              [attr.aria-label]="'Remove ' + (titles()[id] || id)">Remove</button>
          </li>
        }
      </ul>
      <label>Find a document
        <input type="search" [ngModel]="term()" (ngModelChange)="search($event)"
          (focus)="open()" [disabled]="disabled()" placeholder="Search by title" autocomplete="off" />
      </label>
      @if (searching()) { <p role="status">Searching documents…</p> }
      @if (error()) { <p role="alert">{{ error() }}</p> }
      @if (expanded()) {
        <ul class="results" aria-label="Available documents">
          @for (doc of results(); track doc.uid) {
            <li><button type="button" (click)="select(doc)" [disabled]="values().includes(doc.uid)">
              {{ doc.title || doc.properties?.['dc:title'] || doc.uid }}
            </button></li>
          } @empty { @if (!searching()) { <li>No matching documents.</li> } }
        </ul>
        <button type="button" (click)="expanded.set(false)">Close document results</button>
      }
    </fieldset>
  `,
  styles: `
    :host { display:block; min-width: 15rem; }
    fieldset { margin:0; border:1px solid var(--nuxeo-border,#d7dce2); border-radius:4px; padding:.65rem; }
    legend { font-size:.8rem; } label { display:grid; gap:.25rem; font-size:.8rem; }
    input { padding:.5rem; border:1px solid #aeb7c2; border-radius:3px; font:inherit; min-width:0; }
    ul { list-style:none; padding:0; margin:.35rem 0; } li { margin:.25rem 0; }
    .selected li { display:flex; justify-content:space-between; gap:.5rem; overflow-wrap:anywhere; }
    button { cursor:pointer; border:1px solid #b9c3cd; padding:.3rem .5rem; background:var(--nuxeo-box,#fff); color:inherit; border-radius:3px; font:inherit; }
    button:disabled { opacity:.55; cursor:default; } button:focus-visible,input:focus-visible { outline:2px solid #326cb0; outline-offset:2px; }
    .results { max-height:12rem; overflow:auto; } .results button { width:100%; text-align:left; }
  `,
})
export class CpSettingsDocumentPickerComponent {
  readonly values = input<readonly string[]>([]);
  readonly multiple = input(true);
  readonly disabled = input(false);
  readonly label = input('Selected documents');
  readonly valuesChange = output<string[]>();
  readonly term = signal('');
  readonly results = signal<PickerDocument[]>([]);
  readonly titles = signal<Record<string, string>>({});
  readonly expanded = signal(false);
  readonly searching = signal(false);
  readonly error = signal('');
  private readonly api = inject(CpApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<string>();
  private readonly pendingTitles = new Set<string>();
  constructor() {
    this.requests.pipe(debounceTime(200), switchMap(term => {
      this.searching.set(true); this.error.set('');
      const literal = nxqlSearchLiteral(term);
      const titleFilter = term ? ` AND dc:title ILIKE '%${literal}%'` : '';
      return this.api.execute<{ entries?: PickerDocument[] }>('Document.Query', {
        query: `SELECT * FROM Document WHERE ecm:isTrashed = 0 AND ecm:isProxy = 0 AND ecm:isVersion = 0${titleFilter} ORDER BY dc:title`,
        pageSize: 15, currentPageIndex: 0,
      }).pipe(catchError(() => { this.error.set('Documents could not be loaded. Try searching again.'); return of({ entries: [] }); }));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.results.set(result.entries ?? []); this.searching.set(false);
      const titles = { ...this.titles() };
      for (const doc of result.entries ?? []) titles[doc.uid] = doc.title || String(doc.properties?.['dc:title'] || doc.uid);
      this.titles.set(titles);
    });
    effect(() => {
      const titles = untracked(this.titles);
      for (const id of this.values()) {
        if (titles[id] || this.pendingTitles.has(id)) continue;
        this.pendingTitles.add(id);
        this.api.getDocument(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: doc => { this.pendingTitles.delete(id); this.titles.update(current => ({ ...current, [id]: doc.title || id })); },
          error: () => { this.pendingTitles.delete(id); this.titles.update(current => ({ ...current, [id]: id })); },
        });
      }
    });
  }
  open(): void { this.expanded.set(true); this.requests.next(this.term()); }
  search(term: string): void { this.term.set(term); this.expanded.set(true); this.requests.next(term); }
  select(doc: PickerDocument): void {
    if (this.disabled()) return;
    this.valuesChange.emit(this.multiple() ? [...new Set([...this.values(), doc.uid])] : [doc.uid]);
    if (!this.multiple()) this.expanded.set(false);
  }
  remove(id: string): void { if (!this.disabled()) this.valuesChange.emit(this.values().filter(item => item !== id)); }
}
