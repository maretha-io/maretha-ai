import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SAVED_SEARCH_GATEWAY, WebUiAppState, type SavedSearchDefinition } from '@maretha/webui-sdk';

@Component({
  selector: 'cp-saved-searches', standalone: true, imports: [FormsModule],
  template: `
    <label for="cp-saved-search">Search Filters</label>
    <select id="cp-saved-search" [ngModel]="selectedId()" (ngModelChange)="select($event)" [disabled]="busy()">
      <option value="">Select a saved search</option>
      @for (search of searches(); track search.id) { <option [value]="search.id">{{ search.title }}</option> }
    </select>
    <details><summary>Saved search actions</summary>
      <label for="cp-saved-title">Name</label><input id="cp-saved-title" name="savedTitle" [(ngModel)]="title" />
      <div class="buttons">
        <button type="button" [disabled]="busy() || !title.trim() || !gateway" (click)="save(false)">Save as new</button>
        @if (selectedId() && writable()) { <button type="button" [disabled]="busy() || !title.trim()" (click)="save(true)">Update</button> }
        @if (selectedId() && removable()) { <button type="button" [disabled]="busy()" (click)="remove()">Delete</button> }
        <button type="button" [disabled]="busy()" (click)="load()">Refresh</button>
      </div>
      @if (selectedId() && shareable()) {
        <details><summary>Share saved search</summary>
          <label for="cp-share-principal">User or group</label><input id="cp-share-principal" name="principal" [(ngModel)]="principal" />
          <label><input type="checkbox" name="notify" [(ngModel)]="notify" />Notify the user</label>
          <button type="button" [disabled]="busy() || !principal.trim()" (click)="share()">Share</button>
        </details>
      }
    </details>
    @if (error()) { <p role="alert">{{ error() }}</p> }
    @if (status()) { <p role="status">{{ status() }}</p> }
  `,
  styles: ':host{display:flex;flex-direction:column;gap:8px}select,input{padding:8px;width:100%;min-width:0}details{padding:8px 0}details>input{margin:8px 0}.buttons{display:flex;flex-wrap:wrap;gap:6px}button{padding:6px 10px}label:has(input[type=checkbox]){display:flex;gap:8px;align-items:center}input[type=checkbox]{width:auto}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSavedSearchesComponent {
  readonly params = input.required<Readonly<Record<string, unknown>>>();
  readonly selected = output<Readonly<Record<string, unknown>>>();
  readonly gateway = inject(SAVED_SEARCH_GATEWAY, { optional: true });
  private readonly app = inject(WebUiAppState);
  readonly searches = signal<readonly SavedSearchDefinition[]>([]);
  readonly selectedId = signal('');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly status = signal('');
  private readonly permissions = signal<readonly string[]>([]);
  readonly writable = computed(() => this.permissions().includes('WriteProperties') || this.permissions().includes('Everything'));
  readonly removable = computed(() => this.permissions().includes('Remove') || this.permissions().includes('Everything'));
  readonly shareable = computed(() => this.permissions().includes('ManageSecurity') || this.permissions().includes('Everything'));
  title = '';
  principal = '';
  notify = false;
  private generation = 0;
  private disposed = false;

  constructor() {
    effect(() => { this.app.user(); void this.load(); });
    inject(DestroyRef).onDestroy(() => { this.disposed = true; this.generation++; });
  }
  async load(): Promise<void> {
    if (!this.gateway) return;
    const generation = ++this.generation;
    try {
      const entries = await this.gateway.list('searchByEmbeddings');
      if (!this.disposed && generation === this.generation) this.searches.set([...entries].sort((a, b) => a.title.localeCompare(b.title)));
    } catch { if (!this.disposed && generation === this.generation) this.error.set('Saved searches could not be loaded.'); }
  }
  async select(id: string): Promise<void> {
    this.selectedId.set(id); this.permissions.set([]); this.status.set(''); this.error.set('');
    const current = this.searches().find(search => search.id === id);
    if (!current) { this.title = ''; return; }
    this.title = current.title;
    this.selected.emit(current.params);
    try {
      const permissions = await this.gateway?.permissions(id);
      if (!this.disposed && this.selectedId() === id) this.permissions.set(permissions || []);
    } catch { if (!this.disposed && this.selectedId() === id) this.error.set('Saved search permissions could not be checked.'); }
  }
  async save(update: boolean): Promise<void> {
    if (!this.gateway || this.busy() || !this.title.trim()) return;
    const id = this.selectedId();
    if (update && (!id || !this.writable())) return;
    this.busy.set(true); this.error.set(''); this.status.set('');
    try {
      const saved = update
        ? await this.gateway.update(id, 'searchByEmbeddings', this.title.trim(), this.params())
        : await this.gateway.save('searchByEmbeddings', this.title.trim(), this.params());
      if (this.disposed) return;
      await this.load(); await this.select(saved.id); this.status.set('Saved search saved.');
    } catch { if (!this.disposed) this.error.set('The saved search could not be saved. Your filters are unchanged.'); }
    finally { if (!this.disposed) this.busy.set(false); }
  }
  async remove(): Promise<void> {
    const id = this.selectedId();
    if (!this.gateway || this.busy() || !id || !this.removable()) return;
    if (!globalThis.confirm(`Delete saved search “${this.title}”?`)) return;
    this.busy.set(true); this.error.set('');
    try {
      await this.gateway.remove(id);
      if (this.disposed) return;
      await this.select(''); await this.load(); this.status.set('Saved search deleted.');
    } catch { if (!this.disposed) this.error.set('The saved search could not be deleted.'); }
    finally { if (!this.disposed) this.busy.set(false); }
  }
  async share(): Promise<void> {
    const id = this.selectedId();
    if (!this.gateway || this.busy() || !id || !this.shareable() || !this.principal.trim()) return;
    this.busy.set(true); this.error.set('');
    try { await this.gateway.share(id, this.principal.trim(), this.notify); if (!this.disposed) this.status.set('Saved search shared with read access.'); }
    catch { if (!this.disposed) this.error.set('The saved search could not be shared.'); }
    finally { if (!this.disposed) this.busy.set(false); }
  }
}
