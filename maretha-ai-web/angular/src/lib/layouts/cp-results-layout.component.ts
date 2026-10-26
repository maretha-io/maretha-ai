import { NgComponentOutlet, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, TemplateRef, inject, viewChild } from '@angular/core';
import { DOCUMENT_CONTENT_RENDERER, type DocumentLayoutContext } from '@maretha/webui-sdk';
import type { CpDocument } from '../shared/types/cp-models';
import { CpResultsComponent } from '../features/interactions/cp-results.component';

/** Places the CP response in the host's viewer column, alongside its native details. */
@Component({
  selector: 'cp-results-layout',
  standalone: true,
  imports: [CpResultsComponent, NgComponentOutlet, NgTemplateOutlet],
  template: `@if (document; as current) {
    <ng-template #response><cp-results [document]="current" [includeRelated]="false" /></ng-template>
    @if (viewer) { <ng-container *ngComponentOutlet="viewer; inputs: viewerInputs" /> }
    @else { <ng-container *ngTemplateOutlet="response" /> }
  }`,
  styles: ':host{display:flex;flex-direction:column;flex:1;min-height:0}cp-results{display:block;padding:16px}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpResultsLayoutComponent {
  @Input() layoutContext?: DocumentLayoutContext;
  readonly viewer = inject(DOCUMENT_CONTENT_RENDERER, { optional: true });
  readonly response = viewChild<TemplateRef<unknown>>('response');
  get document(): CpDocument | null { return this.layoutContext?.document as CpDocument | null ?? null; }
  get viewerInputs(): Record<string, unknown> { return { document: this.document, noEdit: this.layoutContext?.noEdit, mainContentTemplate: this.response() }; }
}
