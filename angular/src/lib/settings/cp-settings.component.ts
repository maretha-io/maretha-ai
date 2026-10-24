import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, OnDestroy, OnInit, afterRenderEffect, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GuardedView, PageContext, ViewDeparture } from '@maretha/webui-sdk';
import { firstValueFrom, forkJoin } from 'rxjs';
import { CpApiService } from '../cp-api.service';
import { CpSettingsDocumentPickerComponent } from './cp-settings-document-picker.component';
import {
  ES_FIELDS, EVENT_CHOICES, MODEL_CHOICES, PARAMETER_TYPES, EventDraft, InteractionDraft,
  ParameterDraft, SchemaDraft, SettingsDraft, TypeSchemas, clone, newInteraction, newParameter,
  normalizeSettings, schemaKey, settingsPayload, validEvent,
} from './settings-state';

type SettingsTab = 'interactions' | 'metadata' | 'events' | 'settings';
interface Confirmation { message: string; action: string; resolve: (allow: boolean) => void; }
@Component({
  selector: 'cp-settings', standalone: true,
  imports: [FormsModule, CpSettingsDocumentPickerComponent],
  templateUrl: './cp-settings.component.html', styleUrl: './cp-settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSettingsComponent implements OnInit, OnDestroy, GuardedView {
  readonly context = input.required<PageContext>();
  readonly tabs: readonly { id: SettingsTab; label: string }[] = [
    { id: 'interactions', label: 'Interactions' }, { id: 'metadata', label: 'Metadata' },
    { id: 'events', label: 'Events' }, { id: 'settings', label: 'Settings' },
  ];
  readonly models = MODEL_CHOICES;
  readonly parameterTypes = PARAMETER_TYPES;
  readonly eventChoices = EVENT_CHOICES;
  readonly esFields = ES_FIELDS;
  readonly flagChoices: readonly { key: keyof InteractionDraft; label: string; hint: string }[] = [
    { key: 'active', label: 'Enabled', hint: 'Enable this interaction.' },
    { key: 'activeOnActionMenu', label: 'Visible in action menu', hint: 'Make the interaction available to users.' },
    { key: 'activeOnFolder', label: 'Executable on folders', hint: 'Allow this interaction on folders and collections.' },
    { key: 'chat', label: 'Chat', hint: 'Use this interaction for document chat.' },
    { key: 'saveMetadata', label: 'Save metadata', hint: 'Use the response to fill document metadata.' },
    { key: 'mainMetadata', label: 'Main metadata', hint: 'Use this interaction as the default metadata interaction.' },
    { key: 'sendParentsContext', label: 'Send parents context', hint: 'Include parent documents as context.' },
    { key: 'sendAttachments', label: 'Send attachments', hint: 'Include document attachments in the request.' },
    { key: 'requiresUserInput', label: 'Requires user input', hint: 'Wait for user input before running.' },
  ];
  readonly selectedTab = signal<SettingsTab>('interactions');
  readonly draft = signal<SettingsDraft>(normalizeSettings(null));
  readonly documentTypes = signal<string[]>([]);
  readonly selectedType = signal('');
  readonly schemas = signal<SchemaDraft[]>([]);
  readonly selectedSchema = signal<SchemaDraft | null>(null);
  readonly schemaLoading = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly ready = signal(false);
  readonly dirty = signal(false);
  readonly error = signal('');
  readonly status = signal('');
  readonly confirmation = signal<Confirmation | null>(null);
  readonly invalidEvents = computed(() => this.draft().eventsSettings.some(item => !validEvent(item)));
  readonly metadataInteractions = computed(() => this.draft().interactions.filter(item => item.saveMetadata));
  newInteractionName = '';
  private readonly api = inject(CpApiService);
  private readonly destroyRef = inject(DestroyRef);
  private baseline = '';
  private readonly schemaDrafts = new Map<string, SchemaDraft>();
  private readonly schemaBaselines = new Map<string, string>();
  private loadGeneration = 0;
  private schemaGeneration = 0;
  private disposed = false;
  private readonly confirmationDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmationDialog');

  constructor() {
    afterRenderEffect(() => {
      const dialog = this.confirmationDialog()?.nativeElement;
      if (this.confirmation() && dialog && !dialog.open) dialog.showModal();
    });
  }

  ngOnInit(): void { if (this.context().user?.isAdministrator) this.load(); }
  ngOnDestroy(): void {
    this.disposed = true; this.loadGeneration++; this.schemaGeneration++;
    this.answerConfirmation(false);
  }
  load(): void {
    const generation = ++this.loadGeneration;
    this.schemaGeneration++;
    this.schemaLoading.set(false);
    this.loading.set(true); this.error.set(''); this.status.set('');
    forkJoin({
      settings: this.api.execute<Partial<SettingsDraft>>('GetSettingsOperation'),
      types: this.api.execute<string[]>('GetDocumentTypesOperation'),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (generation !== this.loadGeneration) return;
        this.draft.set(normalizeSettings(result.settings)); this.documentTypes.set(result.types ?? []);
        this.baseline = JSON.stringify(settingsPayload(this.draft()));
        this.schemaDrafts.clear(); this.schemaBaselines.clear();
        this.schemas.set([]); this.selectedSchema.set(null); this.selectedType.set('');
        this.dirty.set(false); this.loading.set(false); this.ready.set(true);
      },
      error: () => {
        if (generation !== this.loadGeneration) return;
        this.loading.set(false); this.error.set('AI settings could not be loaded. Try again.');
      },
    });
  }
  changed(): void {
    this.draft.update(value => ({ ...value }));
    this.status.set(''); this.updateDirty();
  }
  schemaChanged(): void { this.status.set(''); this.updateDirty(); }
  private updateDirty(): void {
    this.dirty.set(JSON.stringify(settingsPayload(this.draft())) !== this.baseline ||
      [...this.schemaDrafts].some(([key, schema]) => JSON.stringify(schema) !== this.schemaBaselines.get(key)));
  }
  setTab(tab: SettingsTab): void { this.selectedTab.set(tab); }
  async discard(): Promise<void> {
    if (!this.dirty() || await this.confirm('Discard all unsaved AI settings changes?', 'Discard changes')) this.load();
  }
  async refresh(): Promise<void> {
    if (!this.dirty() || await this.confirm('Reload saved AI settings and discard your unsaved changes?', 'Reload settings')) this.load();
  }
  addInteraction(): void {
    const name = this.newInteractionName.trim();
    if (!name) { this.error.set('Enter a name for the new interaction.'); return; }
    this.draft().interactions.push(newInteraction(name, globalThis.crypto?.randomUUID?.() ?? String(Date.now())));
    this.newInteractionName = ''; this.error.set(''); this.changed();
  }
  async deleteInteraction(interaction: InteractionDraft): Promise<void> {
    if (!await this.confirm(`Delete interaction “${interaction.name || 'Unnamed interaction'}”?`, 'Delete interaction')) return;
    this.draft().interactions = this.draft().interactions.filter(item => item !== interaction);
    // Existing event mappings are retained and surfaced as validation errors until repaired.
    this.changed();
  }
  addParameter(interaction: InteractionDraft): void { interaction.parameters.push(newParameter()); this.changed(); }
  async deleteParameter(interaction: InteractionDraft, parameter: ParameterDraft): Promise<void> {
    if (!await this.confirm(`Delete parameter “${parameter.name || 'Unnamed parameter'}”?`, 'Delete parameter')) return;
    interaction.parameters = interaction.parameters.filter(item => item !== parameter); this.changed();
  }
  setFlag(interaction: InteractionDraft, key: keyof InteractionDraft, checked: boolean): void {
    interaction[key] = checked; this.changed();
  }
  flag(interaction: InteractionDraft, key: keyof InteractionDraft): boolean { return !!interaction[key]; }
  defaultValueType(parameter: ParameterDraft): string { return parameter.type === 'number' ? 'number' : 'text'; }
  setDefault(parameter: ParameterDraft, value: unknown): void {
    // Schema stores parameter values as strings, including booleans and structured JSON.
    parameter.value = String(value ?? ''); this.changed();
  }
  setDocuments(parameter: ParameterDraft, ids: string[]): void {
    parameter.selectedDocumentIds = ids;
    if (parameter.type !== 'array') parameter.selectedDocumentId = ids[0] ?? '';
    this.changed();
  }
  documentIds(parameter: ParameterDraft): string[] {
    return parameter.selectedDocumentIds.length ? parameter.selectedDocumentIds : parameter.selectedDocumentId ? [parameter.selectedDocumentId] : [];
  }
  addEvent(): void {
    this.draft().eventsSettings.push({ docType: '', events: [], interactions: [], active: false }); this.changed();
  }
  async deleteEvent(event: EventDraft): Promise<void> {
    if (!await this.confirm('Delete these document event settings?', 'Delete event mapping')) return;
    this.draft().eventsSettings = this.draft().eventsSettings.filter(item => item !== event); this.changed();
  }
  eventValid(event: EventDraft): boolean { return validEvent(event); }
  missingEventInteractions(event: EventDraft): boolean {
    return event.interactions.some(id => !this.draft().interactions.some(item => item.id === id));
  }
  async selectType(type: string): Promise<void> {
    const generation = ++this.schemaGeneration;
    this.selectedType.set(type); this.selectedSchema.set(null); this.schemas.set([]); this.error.set(''); this.schemaLoading.set(false);
    if (!type) return;
    this.schemaLoading.set(true);
    try {
      const result = await firstValueFrom(this.api.execute<TypeSchemas[]>('GetDocumentTypesOperation', {
        withSchemas: true, documentType: type,
      }));
      if (this.disposed || generation !== this.schemaGeneration) return;
      const schemas = (result?.[0]?.schemas ?? []).map(value => {
        const schema = clone({ ...value, docType: type });
        schema.fields = (schema.fields ?? []).map(field => ({ ...field, active: !!field.active, additionalContext: !!field.additionalContext,
          inIsolation: !!field.inIsolation, interactionId: field.interactionId ?? '', description: field.description ?? '' }));
        const key = schemaKey(schema);
        if (!this.schemaDrafts.has(key)) {
          this.schemaDrafts.set(key, schema); this.schemaBaselines.set(key, JSON.stringify(schema));
        }
        return this.schemaDrafts.get(key)!;
      });
      this.schemas.set(schemas);
    } catch {
      if (!this.disposed && generation === this.schemaGeneration) this.error.set('Schemas could not be loaded. Select the document type again to retry.');
    } finally {
      if (!this.disposed && generation === this.schemaGeneration) this.schemaLoading.set(false);
    }
  }
  selectSchema(name: string): void { this.selectedSchema.set(this.schemas().find(item => item.name === name) ?? null); }
  isolationChanged(field: SchemaDraft['fields'][number]): void {
    if (field.inIsolation && !field.interactionId) {
      const options = this.metadataInteractions();
      field.interactionId = (options.find(item => item.mainMetadata) ?? options[0])?.id ?? '';
    }
    this.schemaChanged();
  }
  validationError(): string {
    if (this.draft().interactions.some(item => !item.name.trim())) return 'Every interaction needs a name.';
    if (this.draft().eventsSettings.some(item => !validEvent(item))) return 'Complete each event mapping: document type, at least one event and at least one interaction.';
    if (this.draft().eventsSettings.some(item => this.missingEventInteractions(item))) return 'An event mapping references a deleted interaction. Choose an existing interaction before saving.';
    return '';
  }
  async save(): Promise<void> {
    if (this.saving() || !this.dirty() || !this.context().user?.isAdministrator) return;
    const validation = this.validationError();
    if (validation) { this.error.set(validation); return; }
    this.saving.set(true); this.error.set(''); this.status.set('');
    const changedSchemas = [...this.schemaDrafts].filter(([key, schema]) => JSON.stringify(schema) !== this.schemaBaselines.get(key));
    try {
      await firstValueFrom(this.api.execute('SaveInteractionsOperation', settingsPayload(this.draft())));
      // The operation accepts one schema per call. Save all edited schemas, not just the visible one.
      for (const [, schema] of changedSchemas) await firstValueFrom(this.api.execute('SaveInteractionsOperation', { schemaSettings: clone(schema) }));
      if (this.disposed) return;
      this.baseline = JSON.stringify(settingsPayload(this.draft()));
      for (const [key, schema] of changedSchemas) this.schemaBaselines.set(key, JSON.stringify(schema));
      this.updateDirty(); this.status.set('Changes saved.');
    } catch {
      if (!this.disposed) this.error.set('Some changes could not be saved. Your edits are retained; retry Save changes.');
    } finally { if (!this.disposed) this.saving.set(false); }
  }
  hasPendingChanges(): boolean { return this.dirty() || this.saving(); }
  canDeactivate(_departure: ViewDeparture): boolean | Promise<boolean> {
    if (this.saving()) { this.error.set('Wait for settings to finish saving before leaving.'); return false; }
    return !this.dirty() || this.confirm('Leave AI settings and discard your unsaved changes?', 'Discard and leave');
  }
  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasPendingChanges()) { event.preventDefault(); event.returnValue = ''; }
  }
  private confirm(message: string, action: string): Promise<boolean> {
    this.answerConfirmation(false);
    return new Promise(resolve => this.confirmation.set({ message, action, resolve }));
  }
  answerConfirmation(allow: boolean): void {
    this.confirmationDialog()?.nativeElement.close();
    const confirmation = this.confirmation(); this.confirmation.set(null); confirmation?.resolve(allow);
  }
}
