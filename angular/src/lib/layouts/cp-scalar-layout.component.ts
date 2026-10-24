import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DOCUMENT_LAYOUT_HOST, SchemaLayoutComponent,
  type DocumentLayout, type DocumentLayoutContext, type GuardedView, type LayoutDocument,
} from '@maretha/webui-sdk';
import { Subscription } from 'rxjs';
import { CpApiService } from '../cp-api.service';
import type { CpDocument } from '../cp-models';
import { CP_DUBLINCORE_FIELDS } from './cp-layout-fields';

@Component({
  selector: 'cp-scalar-layout',
  standalone: true,
  imports: [SchemaLayoutComponent, RouterLink],
  template: `
    @if (layoutContext && layout) {
      @if (layoutContext.mode === 'metadata' && relatedDocument(); as related) {
        <div class="related"><strong>Related Document</strong>
          <a [routerLink]="['/browse/documents', related.uid]">{{ related.title || related.properties['dc:title'] || related.uid }}</a>
        </div>
      }
      @if (relatedError()) { <p role="status">{{ relatedError() }}</p> }
      <nx-schema-layout [class.readonly-metadata]="layoutContext.mode === 'metadata'" [layout]="layout" [documentId]="documentId" [parentId]="parentId"
        [name]="name" [mode]="layoutContext.mode" [noEdit]="layoutContext.noEdit"
        (valueChanged)="rename($event)" (saved)="saved($event)" />
      @if (host) { <button type="button" (click)="cancel()">{{ layoutContext.mode === 'metadata' ? 'Close' : 'Cancel' }}</button> }
    }
  `,
  styles: `nx-schema-layout.readonly-metadata{white-space:pre-wrap}:host{display:block;padding:16px}.related{display:flex;flex-direction:column;gap:6px;margin-bottom:20px}button{padding:8px 16px;margin-top:12px}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpScalarLayoutComponent implements OnChanges, OnDestroy, GuardedView {
  @Input() layoutContext?: DocumentLayoutContext;
  readonly host = inject(DOCUMENT_LAYOUT_HOST, { optional: true });
  private readonly api = inject(CpApiService);
  readonly relatedDocument = signal<CpDocument | null>(null);
  readonly relatedError = signal('');
  private relatedRequest?: Subscription;
  private readonly editor = viewChild(SchemaLayoutComponent);
  layout?: DocumentLayout;
  documentId?: string;
  parentId?: string;
  name = '';
  private disposed = false;

  ngOnChanges(): void {
    this.relatedRequest?.unsubscribe();
    this.relatedDocument.set(null);
    this.relatedError.set('');
    const context = this.layoutContext;
    if (!context) return;
    const document = context.document as CpDocument | null;
    this.documentId = document?.uid;
    this.parentId = context.parentDocument?.['uid'] as string | undefined;
    this.name = String(document?.properties?.['dc:title'] || document?.type || 'Document');
    this.layout = {
      id: `io.maretha.cp.layout.${context.documentType}.${context.mode}`,
      documentType: document?.type || String(context.documentType),
      preserveSchemaConstraints: true,
      fields: context.mode === 'create' ? [
        { property: 'dc:title', label: 'Title' },
        { property: 'dc:description', label: 'Comments' },
        { property: 'file:content', label: 'Content', required: true },
      ] : context.mode === 'metadata' ? CP_DUBLINCORE_FIELDS
        .filter(field => {
          const value = document?.properties?.[field.property];
          return field.property === 'dc:title' || (Array.isArray(value) ? value.length > 0 : !!value);
        })
        .map(field => {
          // Metadata is plain text; the textarea belongs only to the edit form.
          if (field.widget !== 'cp-textarea') return field;
          const { widget, ...readonlyField } = field;
          return readonlyField;
        }) : CP_DUBLINCORE_FIELDS,
    };
    if (context.mode === 'metadata') {
      const relatedId = document?.properties?.['interactionresults:interactions']?.[0]?.documentId;
      if (typeof relatedId === 'string' && relatedId) this.relatedRequest = this.api.getDocument(relatedId).subscribe({
        next: related => this.relatedDocument.set(related),
        error: () => this.relatedError.set('The related document is unavailable or you do not have access to it.'),
      });
    }
  }

  rename(values: Readonly<Record<string, unknown>>): void {
    if (this.layoutContext?.mode === 'create') this.name = String(values['dc:title'] || '').replace(/[/\\]/g, '-').trim();
  }
  saved(document: LayoutDocument): void { this.host?.complete([document]); }
  async cancel(): Promise<void> {
    const context = this.layoutContext;
    try {
      if (await this.canDeactivate() && !this.disposed && context === this.layoutContext) this.host?.cancel();
    } catch { /* Keep the draft open when its departure guard cannot resolve. */ }
  }
  canDeactivate(): boolean | Promise<boolean> { return this.editor()?.canDeactivate() ?? true; }
  hasPendingChanges(): boolean { return this.editor()?.hasPendingChanges() ?? false; }
  ngOnDestroy(): void { this.disposed = true; this.relatedRequest?.unsubscribe(); }
}
