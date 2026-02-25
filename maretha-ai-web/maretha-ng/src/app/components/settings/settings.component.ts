import { ChangeDetectorRef, Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NuxeoService } from '../../nuxeo.service';

type TabName = 'interactions' | 'metadata' | 'events' | 'settings';

@Component({
  selector: 'maretha-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  encapsulation: ViewEncapsulation.ShadowDom,
  template: `
    <div class="settings-page">
      <!-- Header -->
      <div class="page-header">
        <h2>AI Interactions Management</h2>
        <div class="header-actions">
          <button class="btn-primary" (click)="saveChanges()" [disabled]="!isDirty || loading">Save changes</button>
          <button class="btn-icon" (click)="loadData()" [disabled]="loading" title="Refresh">↺</button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tabs">
        <button *ngFor="let tab of tabs" class="tab-btn" [class.active]="selectedTab === tab.id" (click)="selectTab(tab.id)">
          {{ tab.label }}
        </button>
      </div>

      <!-- Loading overlay -->
      <div class="loading-overlay" *ngIf="loading">
        <span>Loading...</span>
      </div>

      <!-- Tab Content: Interactions -->
      <div *ngIf="selectedTab === 'interactions'" class="tab-content">
        <div *ngFor="let interaction of interactions; let i = index" class="interaction-card">
          <div class="card-header">
            <strong>{{ interaction.name }}</strong>
            <span class="tag">{{ interaction.type }}</span>
          </div>
          <div class="card-body">
            <label class="field-label">Name</label>
            <input class="field-input" [(ngModel)]="interaction.name" (ngModelChange)="markDirty('interactions')" />
            <label class="field-label">Description</label>
            <textarea class="field-textarea" [(ngModel)]="interaction.description" (ngModelChange)="markDirty('interactions')"></textarea>
          </div>
        </div>
        <div *ngIf="interactions.length === 0" class="empty-state">No interactions configured.</div>
      </div>

      <!-- Tab Content: Metadata -->
      <div *ngIf="selectedTab === 'metadata'" class="tab-content">
        <p class="info-text">Metadata extraction configuration is managed through interactions with <code>saveMetadata: true</code>.</p>
        <div *ngFor="let docType of documentTypes" class="doc-type-card">
          <strong>{{ docType.label || docType.id }}</strong>
          <code class="doc-type-id">{{ docType.id }}</code>
        </div>
      </div>

      <!-- Tab Content: Events -->
      <div *ngIf="selectedTab === 'events'" class="tab-content">
        <div *ngFor="let event of eventsSettings" class="event-card">
          <div class="event-header">
            <strong>{{ event.eventId }}</strong>
            <label class="toggle">
              <input type="checkbox" [(ngModel)]="event.enabled" (ngModelChange)="markDirty('eventsSettings')" />
              <span>Enabled</span>
            </label>
          </div>
          <select class="field-select" [(ngModel)]="event.interactionId" (ngModelChange)="markDirty('eventsSettings')">
            <option value="">— None —</option>
            <option *ngFor="let i of interactions" [value]="i.id">{{ i.name }}</option>
          </select>
        </div>
        <div *ngIf="eventsSettings.length === 0" class="empty-state">No event triggers configured.</div>
      </div>

      <!-- Tab Content: Settings -->
      <div *ngIf="selectedTab === 'settings'" class="tab-content">
        <div class="settings-group" *ngIf="aiSettings">
          <h3>AI Settings</h3>
          <label class="field-label">Model</label>
          <input class="field-input" [(ngModel)]="aiSettings.model" (ngModelChange)="markDirty('aiSettings')" />
          <label class="field-label">Base URL</label>
          <input class="field-input" [(ngModel)]="aiSettings.baseUrl" (ngModelChange)="markDirty('aiSettings')" />
          <label class="field-label">API Key</label>
          <input class="field-input" type="password" [(ngModel)]="aiSettings.apiKey" (ngModelChange)="markDirty('aiSettings')" />
        </div>
        <div class="settings-group" *ngIf="esSettings">
          <h3>Elasticsearch / OpenSearch Settings</h3>
          <label class="field-label">Index Name</label>
          <input class="field-input" [(ngModel)]="esSettings.indexName" (ngModelChange)="markDirty('esSettings')" />
          <label class="field-label">Embedding Dimensions</label>
          <input class="field-input" type="number" [(ngModel)]="esSettings.embeddingDimensions" (ngModelChange)="markDirty('esSettings')" />
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: var(--nuxeo-app-font, 'Open Sans', Arial, sans-serif);
      color: var(--nuxeo-text-default, #243238);
      --accent: var(--nuxeo-primary-color, #0066ff);
      --border: #e0e0e0;
      --bg-card: #fff;
      --bg-page: var(--nuxeo-page-background, #f5f5f5);
      --radius: 4px;
    }
    .settings-page {
      max-width: 960px;
      margin: 0 auto;
      padding: 24px 32px;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }
    .page-header h2 {
      margin: 0;
      font-size: 1.3rem;
      font-weight: 600;
      color: var(--nuxeo-text-default, #1a1a2e);
    }
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .btn-primary {
      background: var(--accent);
      color: #fff;
      border: none;
      border-radius: var(--radius);
      padding: 8px 20px;
      cursor: pointer;
      font-size: .85rem;
      font-weight: 500;
      letter-spacing: .3px;
      transition: background .2s, box-shadow .2s;
    }
    .btn-primary:hover:not(:disabled) { background: #0052cc; box-shadow: 0 2px 6px rgba(0,102,255,.25); }
    .btn-primary:disabled { opacity: .45; cursor: not-allowed; }
    .btn-icon {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 6px 12px;
      cursor: pointer;
      font-size: 1.1rem;
      transition: background .15s;
    }
    .btn-icon:hover:not(:disabled) { background: #f0f0f0; }
    .tabs {
      display: flex;
      gap: 0;
      border-bottom: 2px solid var(--border);
      margin-bottom: 24px;
    }
    .tab-btn {
      background: none;
      border: none;
      padding: 10px 20px;
      cursor: pointer;
      font-size: .9rem;
      font-weight: 500;
      color: #666;
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      transition: color .15s, border-color .15s;
    }
    .tab-btn:hover { color: var(--accent); }
    .tab-btn.active {
      border-bottom-color: var(--accent);
      color: var(--accent);
      font-weight: 600;
    }
    .tab-content { display: flex; flex-direction: column; gap: 12px; }
    .loading-overlay {
      position: absolute;
      inset: 0;
      background: rgba(255,255,255,.85);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10;
      font-size: .9rem;
      color: #888;
    }
    .interaction-card, .event-card {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 16px 20px;
      transition: box-shadow .15s;
    }
    .interaction-card:hover, .event-card:hover {
      box-shadow: 0 2px 8px rgba(0,0,0,.06);
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    .card-header strong { font-size: .95rem; }
    .card-body { display: flex; flex-direction: column; gap: 8px; }
    .tag {
      background: #e3f2fd;
      color: #1565c0;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: .75rem;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: .4px;
    }
    .field-label {
      font-size: .8rem;
      color: #888;
      margin-top: 4px;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: .3px;
    }
    .field-input, .field-select {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      font-size: .9rem;
      box-sizing: border-box;
      background: #fafafa;
      transition: border-color .15s, background .15s;
    }
    .field-input:focus, .field-select:focus {
      outline: none;
      border-color: var(--accent);
      background: #fff;
      box-shadow: 0 0 0 2px rgba(0,102,255,.1);
    }
    .field-textarea {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      font-size: .9rem;
      min-height: 72px;
      box-sizing: border-box;
      background: #fafafa;
      resize: vertical;
      font-family: inherit;
      transition: border-color .15s, background .15s;
    }
    .field-textarea:focus {
      outline: none;
      border-color: var(--accent);
      background: #fff;
      box-shadow: 0 0 0 2px rgba(0,102,255,.1);
    }
    .toggle { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: .85rem; }
    .toggle input[type="checkbox"] { accent-color: var(--accent); width: 16px; height: 16px; }
    .settings-group {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 20px 24px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 12px;
    }
    .settings-group h3 {
      margin: 0 0 4px;
      font-size: 1rem;
      font-weight: 600;
      color: var(--nuxeo-text-default, #1a1a2e);
    }
    .empty-state {
      color: #aaa;
      text-align: center;
      padding: 40px 16px;
      font-size: .9rem;
    }
    .info-text { color: #666; font-size: .9rem; line-height: 1.5; }
    .info-text code {
      background: #f0f0f0;
      padding: 2px 6px;
      border-radius: 3px;
      font-size: .85rem;
    }
    .doc-type-card {
      background: var(--bg-card);
      display: flex;
      justify-content: space-between;
      align-items: center;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 12px 20px;
    }
    .doc-type-card strong { font-size: .9rem; }
    .doc-type-id {
      font-size: .8rem;
      color: #999;
      font-family: 'SFMono-Regular', Consolas, monospace;
    }
    .event-card .event-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
    }
    .event-card .event-header strong { font-size: .9rem; }
  `]
})
export class MarethaSettingsComponent implements OnInit {
  @Input() user: any;

  tabs = [
    { id: 'interactions' as TabName, label: 'Interactions' },
    { id: 'metadata' as TabName, label: 'Metadata' },
    { id: 'events' as TabName, label: 'Events' },
    { id: 'settings' as TabName, label: 'Settings' },
  ];

  selectedTab: TabName = 'interactions';
  interactions: any[] = [];
  documentTypes: any[] = [];
  eventsSettings: any[] = [];
  aiSettings: any = {};
  esSettings: any = {};
  awsS3AccessEnabled = false;
  isDirty = false;
  loading = false;
  private saveParams: Record<string, any> = {};

  constructor(private nuxeo: NuxeoService, private cdr: ChangeDetectorRef) { }

  ngOnInit() { this.loadData(); }

  selectTab(tab: TabName) { this.selectedTab = tab; }

  markDirty(key: string, value?: any) {
    this.isDirty = true;
    if (key === 'interactions') this.saveParams['interactions'] = this.interactions;
    if (key === 'eventsSettings') this.saveParams['eventsSettings'] = this.eventsSettings;
    if (key === 'aiSettings') this.saveParams['aiSettings'] = this.aiSettings;
    if (key === 'esSettings') this.saveParams['esSettings'] = this.esSettings;
  }

  loadData() {
    this.loading = true;
    this.cdr.markForCheck();
    Promise.all([this.fetchSettings(), this.fetchDocumentTypes()])
      .finally(() => {
        this.loading = false;
        this.isDirty = false;
        this.saveParams = {};
        this.cdr.markForCheck();
      });
  }

  private fetchSettings() {
    return this.nuxeo.executeOperation('GetSettingsOperation', {})
      .then((r: any) => {
        const v = r.value || {};
        if (v.interactions != null) this.interactions = v.interactions;
        if (v.eventsSettings != null) this.eventsSettings = v.eventsSettings;
        if (v.aiSettings != null) this.aiSettings = v.aiSettings;
        if (v.esSettings != null) this.esSettings = v.esSettings;
        if (v.awsS3AccessEnabled != null) this.awsS3AccessEnabled = v.awsS3AccessEnabled;
      })
      .catch((e: any) => console.error('[MarethaSettings] fetchSettings error', e));
  }

  private fetchDocumentTypes() {
    return this.nuxeo.executeOperation('GetDocumentTypesOperation', {})
      .then((r: any) => { this.documentTypes = r.value || []; })
      .catch((e: any) => console.error('[MarethaSettings] fetchDocumentTypes error', e));
  }

  saveChanges() {
    this.loading = true;
    this.nuxeo.executeOperation('SaveInteractionsOperation', this.saveParams)
      .then(() => {
        alert('Changes saved successfully.');
        this.isDirty = false;
        this.saveParams = {};
      })
      .catch((e: any) => {
        alert('Error saving changes: ' + e.message);
        console.error('[MarethaSettings] saveChanges error', e);
      })
      .finally(() => { this.loading = false; this.cdr.markForCheck(); });
  }
}
