import { provideDocumentLayoutExtensions, type DocumentLayoutDefinition } from '@maretha/webui-sdk';
import { CpResultsLayoutComponent } from './cp-results-layout.component';
import { CpScalarLayoutComponent } from './cp-scalar-layout.component';
import { cpUsesFacetLayout } from './cp-layout-fields';

export * from './cp-layout-fields';

export const CP_DOCUMENT_LAYOUTS: readonly DocumentLayoutDefinition[] = Object.freeze([
  ...(['edit', 'metadata', 'view'] as const).map(mode => ({
    id: `io.maretha.cp.layout.interactionresults.${mode}`,
    documentType: 'InteractionResults', mode, priority: 100,
    source: `document/interactionresults/nuxeo-interactionresults-${mode}-layout.html`,
    component: mode === 'view' ? CpResultsLayoutComponent : CpScalarLayoutComponent,
  })),
  ...(['create', 'edit', 'metadata', 'view'] as const).map(mode => ({
    id: `io.maretha.cp.layout.ai.${mode}`,
    mode, priority: 100, when: cpUsesFacetLayout,
    source: `document/ai/nuxeo-ai-${mode}-layout.html`,
    component: mode === 'view' ? CpResultsLayoutComponent : CpScalarLayoutComponent,
  })),
]);

export function provideCpDocumentLayouts() {
  return provideDocumentLayoutExtensions({ id: 'io.maretha.cp.layouts', layouts: CP_DOCUMENT_LAYOUTS });
}
