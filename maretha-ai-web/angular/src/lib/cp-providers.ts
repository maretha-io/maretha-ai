import { Component, ChangeDetectionStrategy, computed, inject, input, makeEnvironmentProviders, viewChild } from '@angular/core';
import {
  ADMINISTRATION_MENU_SLOT, ADMINISTRATION_PAGES_SLOT, DOCUMENT_ACTIONS_SLOT, DRAWER_ITEMS_SLOT, DRAWER_PAGES_SLOT,
  WebUiAppState, defineContribution, defineDocumentView, definePairedView, definePage, provideWebUiExtensions, provideDocumentFieldExtensions, provideDocumentLayoutWidget,
  type AdministrationContext, type GuardedView, type PageContext, type ViewDeparture,
} from '@maretha/webui-sdk';
import { CpAiTabComponent } from './features/interactions/cp-ai-tab.component';
import { CpMetadataActionComponent } from './features/interactions/cp-metadata-action.component';
import { CpSettingsComponent } from './features/settings/cp-settings.component';
import { CpSearchPageComponent, CpSearchDrawerComponent, CpSearchItemComponent, CpSimilarDocumentsComponent } from './features/search/cp-search.component';
import { provideCpDocumentLayouts } from './layouts/cp-document-layouts';
import { cpHasGroup } from './shared/types/cp-models';
import { CP_AI_FIELDS } from './layouts/cp-layout-fields';
import { CpTextareaWidgetComponent } from './layouts/cp-textarea-widget.component';

@Component({
  selector: 'cp-settings-administration-page',
  standalone: true,
  imports: [CpSettingsComponent],
  template: '<cp-settings [context]="pageContext()" />',
  styles: ':host{display:flex;flex:1;min-width:0;min-height:0}cp-settings{flex:1;min-width:0}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSettingsAdministrationPageComponent implements GuardedView {
  readonly context = input.required<AdministrationContext>();
  private readonly app = inject(WebUiAppState);
  private readonly settings = viewChild(CpSettingsComponent);
  readonly pageContext = computed<PageContext>(() => ({
    ...this.context(), pageId: 'maretha-cp-interactions', params: {}, query: this.app.query(),
    locale: this.app.locale(), route: this.app.route(),
  }));
  canDeactivate(departure: ViewDeparture): boolean | Promise<boolean> { return this.settings()?.canDeactivate(departure) ?? true; }
  hasPendingChanges(): boolean { return this.settings()?.hasPendingChanges() ?? false; }
}

/** CP-owned assembly. The application's bridge calls only this public entry point. */
export function provideCpWebUi() {
  return makeEnvironmentProviders([
    provideCpDocumentLayouts(),
    provideDocumentLayoutWidget({ id: 'cp-textarea', component: CpTextareaWidgetComponent }),
    provideDocumentFieldExtensions({ id: 'io.maretha.cp.fields.ai', documentTypes: ['File', 'Picture'],
      modes: ['create', 'edit', 'metadata'], schema: 'ai', fields: CP_AI_FIELDS }),
    provideWebUiExtensions({
      id: 'io.maretha.cp.webui',
      contributions: [
        definePage({ id: 'io.maretha.cp.page.settings', pageId: 'maretha-cp-interactions',
          component: CpSettingsComponent, retain: true, when: context => context.user?.isAdministrator === true }),
        ...definePairedView({ items: ADMINISTRATION_MENU_SLOT, pages: ADMINISTRATION_PAGES_SLOT }, {
          id: 'io.maretha.cp.administration.settings', viewId: 'maretha-cp-interactions', label: 'AI Settings',
          component: CpSettingsAdministrationPageComponent, order: 0, retain: true, pageHeading: 'component',
          when: context => context.user?.isAdministrator === true,
        }),
        defineContribution(DOCUMENT_ACTIONS_SLOT, { id: 'io.maretha.cp.document-actions.metadata',
          component: CpMetadataActionComponent, order: 0,
          when: context => !!context.document && cpHasGroup(context.user, 'AI-METADATA') }),
        ...defineDocumentView({ id: 'io.maretha.cp.document-view.ai', viewId: 'AiTab', label: 'AI',
          component: CpAiTabComponent, order: 10, retain: true, when: context => !!context.document }),
        ...defineDocumentView({ id: 'io.maretha.cp.document-view.similar', viewId: 'SimilarDocumentsTab', label: 'More Like This',
          component: CpSimilarDocumentsComponent, order: 11,
          when: context => !!context.document && Array.isArray(context.document['facets']) &&
            context.document['facets'].includes('InteractionResultsFacet') && cpHasGroup(context.user, 'AI-SEARCH') }),
        defineContribution(DRAWER_ITEMS_SLOT, { id: 'io.maretha.cp.drawer.search', pairKey: 'find', order: 1,
          component: CpSearchItemComponent, when: context => cpHasGroup(context.user, 'AI-SEARCH') }),
        defineContribution(DRAWER_PAGES_SLOT, { id: 'io.maretha.cp.drawer.search', pairKey: 'find', retain: true,
          component: CpSearchDrawerComponent, when: context => cpHasGroup(context.user, 'AI-SEARCH') }),
        definePage({ id: 'io.maretha.cp.page.search', pageId: 'embedding-search',
          component: CpSearchPageComponent, retain: true, when: context => cpHasGroup(context.user, 'AI-SEARCH') }),
      ],
    }),
  ]);
}
