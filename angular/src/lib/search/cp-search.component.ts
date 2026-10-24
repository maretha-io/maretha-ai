import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy, Component, DestroyRef, Injectable, computed, effect, forwardRef, inject, input, signal, untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  DrawerItemComponent, ResultsProjectionComponent, ResultsProjectionTemplateDirective,
  WebUiAppState, WebUiSlotComponent, RESULTS_COMMAND_GATEWAY,
  type DocumentActionContext, type DrawerContext, type PageContext, type ResultsColumnLayout,
  type ResultsProjectionColumn, type ResultsProjectionMode, type ResultsProjectionRow,
  type ResultsSelectionContext, type ResultsToolbarContext,
} from '@maretha/webui-sdk';
import { Subscription } from 'rxjs';
import { CpApiService } from '../cp-api.service';
import type { CpDocument } from '../cp-models';
import { cpConfidence, cpScore, cpSearchParameters, cpSimilarityVector, type CpSearchCriteria } from './search-state';
import { CpSavedSearchesComponent } from './cp-saved-searches.component';

/** Queue state survives opening a result and is reset when the signed-in user changes. */
@Injectable({ providedIn: 'root' })
class CpSearchSession {
  userId = '';
  query = '';
  fulltext = '';
  confidence = 0.8;
  readonly queue = signal(false);
  readonly queueRows = signal<readonly CpDocument[]>([]);
}

@Component({
  selector: 'cp-search-item', standalone: true, imports: [DrawerItemComponent],
  template: '<nx-drawer-item [context]="context()" pageId="find" label="AI search" icon="nuxeo:search" />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSearchItemComponent { readonly context = input.required<DrawerContext>(); }

@Component({
  selector: 'cp-search-drawer', standalone: true, imports: [FormsModule, RouterLink, CpSavedSearchesComponent],
  template: `<h2>AI search</h2>
    <cp-saved-searches [params]="savedParams" (selected)="applySaved($event)" />
    <button class="queue-switch" type="button" (click)="toggleQueue()" [attr.aria-label]="queue() ? 'Switch to Filters View' : 'Switch to Queue View'">{{ queue() ? 'Switch to Filters View' : 'Switch to Queue View' }}</button>
    <form (ngSubmit)="search()" [hidden]="queue()">
    <label for="cp-vector-query">What are you looking for?</label>
    <input id="cp-vector-query" name="vectorQuery" type="search" [(ngModel)]="query" (ngModelChange)="scheduleSearch()" autofocus />
    <label for="cp-confidence">Confidence value <output>{{ confidence }}</output></label>
    <input id="cp-confidence" name="confidence" type="range" min="0" max="1" step="0.1" [(ngModel)]="confidence" (ngModelChange)="scheduleSearch()" />
    <label for="cp-fulltext">Full text</label>
    <input id="cp-fulltext" name="fulltext" type="search" [(ngModel)]="fulltext" (ngModelChange)="scheduleSearch()" placeholder="Additional words in the document" />
    <div class="buttons"><button type="submit">Search</button><button type="button" [disabled]="!query && !fulltext && confidence === 0.8" (click)="clear()">Reset</button></div>
  </form>
  @if (queue()) {
    @if (queueError()) { <p role="alert">{{ queueError() }}</p><button type="button" (click)="loadQueue(false)">Retry</button> }
    @if (queueLoading()) { <p role="status">Loading results…</p> }
    @if (!queueRows().length && !queueLoading()) { <p role="status">No results.</p> }
    <ul class="queue" aria-label="AI search queue">@for (row of queueRows(); track row.uid) {
      <li><a [routerLink]="['/browse/documents', row.uid]" [attr.aria-current]="context().document?.['uid'] === row.uid ? 'page' : null">{{ row.title || row.properties['dc:title'] || row.uid }}</a></li>
    }</ul>
    @if (queueNext()) { <button type="button" [disabled]="queueLoading()" (click)="loadQueue(true)">Load more</button> }
  }`,
  styles: ':host{display:block;padding:20px}form{display:flex;flex-direction:column;gap:12px}form[hidden]{display:none}h2{font-size:20px}input{padding:8px;min-width:0}.buttons{display:flex;gap:8px}button{padding:8px 16px}.queue-switch{margin:12px 0}.queue{list-style:none;padding:0}.queue a{display:block;padding:12px;color:inherit;border-bottom:1px solid var(--nuxeo-border,#ddd)}.queue a[aria-current=page]{font-weight:bold;background:var(--nuxeo-selected-background,#eee)}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSearchDrawerComponent {
  readonly context = input.required<DrawerContext>();
  private readonly app = inject(WebUiAppState);
  private readonly router = inject(Router);
  private readonly api = inject(CpApiService);
  private readonly state = inject(CpSearchSession);
  readonly queue = this.state.queue;
  readonly queueRows = this.state.queueRows;
  readonly queueLoading = signal(false);
  readonly queueError = signal('');
  readonly queueNext = signal(false);
  private queueRequest?: Subscription;
  private queuePage = 0;
  private autoSearch?: ReturnType<typeof setTimeout>;
  get query(): string { return this.state.query; }
  set query(value: string) { this.state.query = value; }
  get fulltext(): string { return this.state.fulltext; }
  set fulltext(value: string) { this.state.fulltext = value; }
  get confidence(): number { return this.state.confidence; }
  set confidence(value: number) { this.state.confidence = cpConfidence(value, 0.8); }
  get savedParams(): Readonly<Record<string, unknown>> { return {
    vectorQuery: this.query, vectorField: 'text', min_score: this.confidence,
    ...(this.fulltext ? { ecm_fulltext: this.fulltext } : {}),
  }; }
  constructor() {
    effect(() => {
      const userId = this.app.user()?.id || '';
      if (this.state.userId !== userId) {
        this.state.userId = userId; this.query = ''; this.fulltext = ''; this.confidence = 0.8;
        this.queue.set(false); this.queueRows.set([]);
      }
      const params = this.app.query();
      if (!this.app.route().split('?')[0].startsWith('/page/embedding-search')) return;
      this.query = params['vectorQuery'] || '';
      this.fulltext = params['ecm_fulltext'] || '';
      this.confidence = cpConfidence(params['min_score'], 0.8);
    });
    inject(DestroyRef).onDestroy(() => { this.queueRequest?.unsubscribe(); if (this.autoSearch) clearTimeout(this.autoSearch); });
  }
  scheduleSearch(): void { if (this.autoSearch) clearTimeout(this.autoSearch); this.autoSearch = setTimeout(() => this.search(), 400); }
  search(): void {
    if (this.autoSearch) clearTimeout(this.autoSearch);
    void this.router.navigate(['/page', 'embedding-search'], { queryParams: {
      vectorQuery: this.query, min_score: cpConfidence(this.confidence, 0.8), ecm_fulltext: this.fulltext || null,
    } });
    if (this.queue()) this.loadQueue(false);
  }
  clear(): void { this.query = ''; this.fulltext = ''; this.confidence = 0.8; this.search(); }
  applySaved(params: Readonly<Record<string, unknown>>): void {
    this.query = typeof params['vectorQuery'] === 'string' ? params['vectorQuery'] : '';
    this.fulltext = typeof params['ecm_fulltext'] === 'string' ? params['ecm_fulltext'] : '';
    this.confidence = cpConfidence(params['min_score'], 0.8);
    this.search();
  }
  toggleQueue(): void { this.queue.update(value => !value); if (this.queue()) this.loadQueue(false); }
  loadQueue(append: boolean): void {
    this.queueRequest?.unsubscribe(); this.queueError.set('');
    if (!append) { this.queuePage = 0; this.queueRows.set([]); }
    else this.queuePage++;
    if (!this.query.trim()) { this.queueLoading.set(false); this.queueNext.set(false); return; }
    this.queueLoading.set(true);
    this.queueRequest = this.api.provider('searchByEmbeddings', cpSearchParameters({ vectorQuery: this.query, vectorField: 'text', minScore: this.confidence, fulltext: this.fulltext }, this.queuePage), 'POST').subscribe({
      next: response => {
        this.queueRows.set([...new Map([...(append ? this.queueRows() : []), ...(response.entries || [])].map(row => [row.uid, row])).values()]);
        this.queueNext.set(response.isNextPageAvailable ?? this.queueRows().length < (response.resultsCount || 0));
        this.queueLoading.set(false);
      }, error: () => { this.queueLoading.set(false); this.queueError.set('The AI search queue could not be loaded.'); },
    });
  }
}

@Component({
  selector: 'cp-search-page', standalone: true, imports: [forwardRef(() => CpEmbeddingResultsComponent)],
  template: '<h1>AI search</h1><cp-embedding-results [criteria]="criteria()" />',
  styles: ':host{display:flex;flex:1;min-height:0;flex-direction:column;padding:20px}h1{font-size:24px}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSearchPageComponent {
  readonly context = input.required<PageContext>();
  readonly criteria = computed<CpSearchCriteria>(() => ({
    vectorQuery: this.context().query['vectorQuery'] || '', vectorField: 'text',
    minScore: cpConfidence(this.context().query['min_score'], 0.8), fulltext: this.context().query['ecm_fulltext'] || '',
  }));
}

@Component({
  selector: 'cp-similar-documents', standalone: true, imports: [forwardRef(() => CpEmbeddingResultsComponent)],
  template: `<label class="confidence">Confidence value <output>{{ minScore() }}</output>
      <input type="range" min="0" max="1" step="0.1" [value]="minScore()" (change)="minScore.set(+$any($event.target).value)" />
    </label><cp-embedding-results [criteria]="criteria()" [sourceDocument]="document()" />`,
  styles: ':host{display:flex;flex:1;min-height:0;flex-direction:column;padding:16px}.confidence{display:flex;flex-direction:column;gap:8px;margin-bottom:16px}input{width:100%}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSimilarDocumentsComponent {
  readonly context = input.required<DocumentActionContext>();
  readonly minScore = signal(0.4);
  readonly document = computed(() => this.context().document as CpDocument | null);
  readonly criteria = computed<CpSearchCriteria>(() => ({
    vectorQuery: cpSimilarityVector(this.document()), vectorField: 'similarity', minScore: this.minScore(),
  }));
}

@Component({
  selector: 'cp-embedding-results', standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, ResultsProjectionComponent, ResultsProjectionTemplateDirective, WebUiSlotComponent],
  template: `
    <div class="tools">
      <span role="status">{{ total() }} results · {{ selection().length }} selected</span>
      <label>Display <select [ngModel]="mode()" (ngModelChange)="setMode($event)"><option value="grid">Grid</option><option value="table">Table</option></select></label>
      <label>Sort by <select [ngModel]="sortBy()" (ngModelChange)="sort($event)">
        <option value="contextParameters.relevance_score">Relevance</option><option value="dc:title">Title</option>
        <option value="dc:created">Created</option><option value="dc:modified">Modified</option>
      </select></label>
      <button type="button" (click)="direction() === 'asc' ? direction.set('desc') : direction.set('asc'); reload()">{{ direction() === 'asc' ? 'Ascending' : 'Descending' }}</button>
      <details><summary>Columns</summary>@for (column of allColumns; track column.id) {
        <label><input type="checkbox" [checked]="!hiddenColumns().includes(column.id)" (change)="toggleColumn(column.id)" />{{ column.label }}</label>
      }</details>
      <button type="button" (click)="reload()" [disabled]="loading()">Refresh</button>
    </div>
    @if (error()) { <p role="alert">{{ error() }}</p><button type="button" (click)="reload()">Retry</button> }
    @if (!hasQuery()) { <p role="status">{{ criteria().vectorField === 'text' ? 'Enter what you are looking for in AI search.' : 'This document has no embeddings for similarity search.' }}</p> }
    @if (selection().length) { <nx-webui-slot name="RESULTS_SELECTION_ACTIONS" [context]="toolbarContext()" /> }
    <nx-results-projection [rows]="rows()" [selection]="selection()" [columns]="columns()" [mode]="mode()"
      [loading]="loading()" [rowActivation]="true" [columnLayoutEnabled]="true" [scope]="criteria()"
      [hostId]="'cp-search-' + criteria().vectorField" [contextEpoch]="epoch()"
      (selectionChange)="selection.set($event)" (rowActivated)="open($event)" (columnLayoutChange)="saveColumns($event)">
      <ng-template nxResultsTemplate="cell:title" let-row="row">
        <a [routerLink]="['/browse/documents', row.uid]">{{ row.title || row.properties?.['dc:title'] || row.uid }}</a>
      </ng-template>
      <ng-template nxResultsTemplate="item" let-row="row" let-selected="selected" let-toggle="toggleSelection">
        <article class="card" [class.selected]="selected">
          <label class="select"><input type="checkbox" [checked]="selected" (change)="toggle()" [attr.aria-label]="'Select ' + (row.title || row.uid)" /></label>
          <a [routerLink]="['/browse/documents', row.uid]">
            @if (thumbnail(row); as image) { <img [src]="image" alt="" loading="lazy" /> }
            @else { <span class="file-icon" aria-hidden="true">▧</span> }
            <strong>{{ row.title || row.properties?.['dc:title'] || row.uid }}</strong>
          </a>
          <span class="score">Relevance {{ score(row) }}</span>
          <small>{{ modified(row) | date:'medium' }}</small>
          <div class="row-actions"><nx-webui-slot name="DOCUMENT_ACTIONS" [context]="rowContext(row)" /></div>
        </article>
      </ng-template>
      <ng-template nxResultsTemplate="action" let-row="row">
        <nx-webui-slot name="DOCUMENT_ACTIONS" [context]="rowContext(row)" />
      </ng-template>
    </nx-results-projection>
    @if (nextPage()) { <button class="more" type="button" [disabled]="loading()" (click)="loadMore()">{{ loading() ? 'Loading…' : 'Load more' }}</button> }
  `,
  styles: `:host{display:flex;flex:1;min-height:0;flex-direction:column;gap:12px}.tools{display:flex;align-items:center;flex-wrap:wrap;gap:12px}.tools>span{margin-right:auto}.tools label{display:flex;align-items:center;gap:6px}select,button{padding:6px 10px}details{position:relative}details[open]{padding:10px;border:1px solid var(--nuxeo-border,#ddd)}details label{display:block}.card{position:relative;display:flex;flex-direction:column;padding:12px;gap:8px;min-width:0;min-height:220px;border:1px solid var(--nuxeo-border,#ddd);background:var(--nuxeo-box,#fff)}.card.selected{outline:2px solid var(--nuxeo-primary-color,#0066cc)}.card>a{display:flex;flex-direction:column;gap:8px;text-decoration:none;color:inherit}.card img{width:100%;height:150px;object-fit:contain}.file-icon{height:150px;display:grid;place-items:center;font-size:64px;color:#657184}.select{position:absolute;top:12px;left:12px;z-index:1}.score{font-size:12px}.row-actions{display:flex;flex-wrap:wrap;max-height:90px;overflow:auto}.more{align-self:center}nx-results-projection{min-height:160px}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpEmbeddingResultsComponent {
  readonly criteria = input.required<CpSearchCriteria>();
  readonly sourceDocument = input<CpDocument | null>(null);
  private readonly api = inject(CpApiService);
  private readonly app = inject(WebUiAppState);
  private readonly router = inject(Router);
  private readonly commands = inject(RESULTS_COMMAND_GATEWAY, { optional: true });
  readonly rows = signal<readonly CpDocument[]>([]);
  readonly selection = signal<readonly string[]>([]);
  readonly total = signal(0);
  readonly nextPage = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly epoch = signal(0);
  readonly mode = signal<ResultsProjectionMode>('grid');
  readonly sortBy = signal('contextParameters.relevance_score');
  readonly direction = signal<'asc' | 'desc'>('desc');
  readonly hiddenColumns = signal<readonly string[]>([]);
  private readonly columnLayout = signal<readonly ResultsColumnLayout[]>([]);
  private request?: Subscription;
  private page = 0;
  private generation = 0;
  private commandBusy = false;
  private readonly actionBusy = signal(false);
  private preferenceScope = '';
  private readonly contexts = new WeakMap<object, DocumentActionContext>();
  readonly hasQuery = computed(() => this.criteria().vectorQuery.length > 0);
  readonly allColumns: readonly ResultsProjectionColumn<CpDocument>[] = [
    { id: 'title', label: 'Title', value: row => row.title || row.properties['dc:title'], flex: 2 },
    { id: 'relevance', label: 'Relevance', value: row => cpScore(row), width: 110 },
    { id: 'modified', label: 'Modified', value: row => row.properties['dc:modified'] },
    { id: 'lastContributor', label: 'Last Contributor', value: row => row.properties['dc:lastContributor'] },
    { id: 'created', label: 'Created', value: row => row.properties['dc:created'], hidden: true },
    { id: 'creator', label: 'Author', value: row => row.properties['dc:creator'], hidden: true },
    { id: 'expired', label: 'Expiration date', value: row => row.properties['dc:expired'], hidden: true },
    { id: 'version', label: 'Version', value: row => row['versionLabel'], hidden: true },
    { id: 'state', label: 'State', value: row => row['state'] },
  ];
  readonly columns = computed(() => {
    const layout = this.columnLayout();
    const ordered = [...layout.map(saved => this.allColumns.find(column => column.id === saved.id)).filter((column): column is ResultsProjectionColumn<CpDocument> => !!column),
      ...this.allColumns.filter(column => !layout.some(saved => saved.id === column.id))];
    return ordered.map(column => ({ ...column, width: layout.find(saved => saved.id === column.id)?.width ?? column.width,
      hidden: this.hiddenColumns().includes(column.id) }));
  });
  readonly toolbarContext = computed<ResultsToolbarContext>(() => {
    const chosen = new Set(this.selection());
    const selectionContext: ResultsSelectionContext = {
      document: this.sourceDocument(), user: this.app.user(), noEdit: false,
      selectedDocuments: this.rows().filter(row => chosen.has(row.uid)), items: this.rows(), displayMode: this.mode(),
      columns: this.columns().filter(column => !column.hidden).map(column => ({ field: column.id, label: column.label })),
      busy: this.loading() || this.actionBusy(), setBusy: busy => this.actionBusy.set(busy),
      refresh: removed => this.refresh(removed), reportError: message => this.error.set(message),
    };
    return { ...selectionContext, queryToken: this.epoch(), routeToken: this.epoch(),
      loadedCount: this.rows().length, totalCount: this.total(), allSelected: chosen.size === this.rows().length,
      availableCommands: this.commands?.available(selectionContext) || [], workflows: [], execute: command => this.execute(command),
    };
  });
  constructor() {
    effect(() => { const scope = this.criteria().vectorField; this.sourceDocument(); untracked(() => {
      if (this.preferenceScope !== scope) { this.preferenceScope = scope; this.restorePreferences(); }
      this.reload();
    }); });
    inject(DestroyRef).onDestroy(() => { this.generation++; this.request?.unsubscribe(); });
    this.hiddenColumns.set(this.allColumns.filter(column => column.hidden).map(column => column.id));
  }
  reload(): void {
    this.page = 0;
    this.selection.set([]);
    this.rows.set([]);
    this.epoch.update(epoch => epoch + 1);
    this.fetch(false);
  }
  private fetch(append: boolean): void {
    this.request?.unsubscribe();
    const generation = ++this.generation;
    this.error.set('');
    if (!this.hasQuery()) { this.loading.set(false); this.total.set(0); this.nextPage.set(false); return; }
    this.loading.set(true);
    this.request = this.api.provider('searchByEmbeddings', cpSearchParameters(this.criteria(), this.page, this.sortBy(), this.direction()), 'POST').subscribe({
      next: result => {
        if (generation !== this.generation) return;
        const entries = result.entries || [];
        const combined = append ? [...this.rows(), ...entries] : entries;
        this.rows.set([...new Map(combined.map(row => [row.uid, row])).values()]);
        this.total.set(result.resultsCount ?? this.rows().length);
        this.nextPage.set(result.isNextPageAvailable ?? (entries.length > 0 && this.rows().length < this.total()));
        this.loading.set(false);
      },
      error: () => { if (generation === this.generation) { this.loading.set(false); this.error.set('AI search failed. Retry or adjust the confidence value.'); } },
    });
  }
  loadMore(): void { if (!this.loading() && this.nextPage()) { this.page++; this.fetch(true); } }
  sort(field: string): void { this.sortBy.set(field); this.direction.set(field === 'dc:title' || field === 'dc:created' ? 'asc' : 'desc'); this.reload(); }
  toggleColumn(id: string): void {
    this.hiddenColumns.update(hidden => hidden.includes(id) ? hidden.filter(value => value !== id) : [...hidden, id]);
    this.savePreferences();
  }
  setMode(mode: ResultsProjectionMode): void { this.mode.set(mode); this.savePreferences(); }
  saveColumns(layout: readonly ResultsColumnLayout[]): void {
    this.columnLayout.set(layout);
    this.savePreferences();
  }
  private restorePreferences(): void {
    try {
      const saved = JSON.parse(localStorage.getItem(`io.maretha.cp.results.${this.preferenceScope}`) || 'null');
      this.mode.set(saved?.mode === 'table' ? 'table' : 'grid');
      this.hiddenColumns.set(Array.isArray(saved?.hidden) ? saved.hidden.filter((id: unknown) => this.allColumns.some(column => column.id === id)) : this.allColumns.filter(column => column.hidden).map(column => column.id));
      this.columnLayout.set(Array.isArray(saved?.columns) ? saved.columns.filter((column: ResultsColumnLayout) =>
        this.allColumns.some(known => known.id === column?.id) && (column.width === undefined || Number.isFinite(column.width))) : []);
    } catch { /* Keep current defaults when browser storage is unavailable. */ }
  }
  private savePreferences(): void {
    try { localStorage.setItem(`io.maretha.cp.results.${this.preferenceScope}`, JSON.stringify({ mode: this.mode(), hidden: this.hiddenColumns(), columns: this.columnLayout() })); }
    catch { /* Session settings remain available. */ }
  }
  open(row: ResultsProjectionRow): void { void this.router.navigate(['/browse/documents', row.uid]); }
  score(row: ResultsProjectionRow): string { return cpScore(row); }
  modified(row: ResultsProjectionRow): string | null { const value = row.properties?.['dc:modified']; return typeof value === 'string' ? value : null; }
  thumbnail(row: ResultsProjectionRow): string {
    const thumbnail = (row['contextParameters'] as Record<string, any> | undefined)?.['thumbnail'];
    return typeof thumbnail?.url === 'string' ? thumbnail.url : '';
  }
  rowContext(row: ResultsProjectionRow): DocumentActionContext {
    let context = this.contexts.get(row);
    if (!context || context.user !== this.app.user()) { context = { document: row, noEdit: false, user: this.app.user(), refreshDocument: () => { void this.refresh(); } }; this.contexts.set(row, context); }
    return context;
  }
  private async refresh(removed: readonly string[] = []): Promise<void> {
    if (removed.length) this.rows.update(rows => rows.filter(row => !removed.includes(row.uid)));
    this.reload();
  }
  private async execute(command: string): Promise<void> {
    if (!this.commands || this.commandBusy || this.loading()) return;
    this.commandBusy = true;
    const epoch = this.epoch();
    try {
      const removed = await this.commands.execute(command, this.toolbarContext(), () => epoch === this.epoch());
      if (epoch !== this.epoch()) return;
      this.selection.set([]);
      if (removed.length) await this.refresh(removed);
    } catch (error) { this.error.set(error instanceof Error ? error.message : 'The selection action failed.'); }
    finally { this.commandBusy = false; }
  }
}
