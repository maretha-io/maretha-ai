import { Component, ChangeDetectionStrategy, input, makeEnvironmentProviders } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ADMINISTRATION_MENU_SLOT, DOCUMENT_ACTIONS_SLOT, DRAWER_ITEMS_SLOT, DRAWER_PAGES_SLOT,
  defineContribution, defineDocumentView, definePage, provideWebUiExtensions, provideDocumentFieldExtensions, provideDocumentLayoutWidget,
  type AdministrationContext,
} from '@maretha/webui-sdk';
import { CpAiTabComponent } from './interactions/cp-ai-tab.component';
import { CpMetadataActionComponent } from './interactions/cp-metadata-action.component';
import { CpSettingsComponent } from './settings/cp-settings.component';
import { CpSearchPageComponent, CpSearchDrawerComponent, CpSearchItemComponent, CpSimilarDocumentsComponent } from './search/cp-search.component';
import { provideCpDocumentLayouts } from './layouts/cp-document-layouts';
import { cpHasGroup } from './cp-models';
import { CP_AI_FIELDS } from './layouts/cp-layout-fields';
import { CpTextareaWidgetComponent } from './layouts/cp-textarea-widget.component';

@Component({
  selector: 'cp-settings-menu',
  standalone: true,
  imports: [RouterLink],
  template: '<a [routerLink]="[\'/page\', \'maretha-cp-interactions\']">AI Settings</a>',
  styles: ':host{display:block}a{display:block;padding:12px 20px;color:inherit;text-decoration:none}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpSettingsMenuComponent { readonly context = input.required<AdministrationContext>(); }

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
        defineContribution(ADMINISTRATION_MENU_SLOT, { id: 'io.maretha.cp.administration.settings',
          component: CpSettingsMenuComponent, order: 100, when: context => context.user?.isAdministrator === true }),
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
