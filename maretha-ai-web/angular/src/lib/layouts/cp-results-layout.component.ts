import { NgComponentOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { DOCUMENT_CONTENT_RENDERER, type DocumentLayoutContext } from '@maretha/webui-sdk';
import type { CpDocument } from '../shared/types/cp-models';
import { CpResultsComponent } from '../features/interactions/cp-results.component';

/** Keeps the host's authenticated file preview and attachment controls below the responses. */
@Component({
  selector: 'cp-results-layout',
  standalone: true,
  imports: [CpResultsComponent, NgComponentOutlet],
  template: `@if (document; as current) {
    <cp-results [document]="current" [includeRelated]="false" />
    @if (viewer) { <ng-container *ngComponentOutlet="viewer; inputs: viewerInputs" /> }
  }`,
  styles: ':host{display:flex;flex-direction:column;flex:1;min-height:0;gap:16px}cp-results{padding:16px}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpResultsLayoutComponent {
  @Input() layoutContext?: DocumentLayoutContext;
  readonly viewer = inject(DOCUMENT_CONTENT_RENDERER, { optional: true });
  get document(): CpDocument | null { return this.layoutContext?.document as CpDocument | null ?? null; }
  get viewerInputs(): Record<string, unknown> { return { document: this.document }; }
}
