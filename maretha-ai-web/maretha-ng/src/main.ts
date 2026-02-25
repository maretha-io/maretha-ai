import { createApplication } from '@angular/platform-browser';
import { createCustomElement } from '@angular/elements';
import { provideHttpClient } from '@angular/common/http';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';

import { MarethaDocumentChatComponent } from './app/components/document-chat/document-chat.component';
import { MarethaDocumentInteractionComponent } from './app/components/document-interaction/document-interaction.component';
import { MarethaSettingsComponent } from './app/components/settings/settings.component';
import { MarethaSimilarDocumentsComponent } from './app/components/similar-documents/similar-documents.component';
import { MarethaEmbeddingSearchComponent } from './app/components/embedding-search/embedding-search.component';

(async () => {
  const appRef = await createApplication({
    providers: [
      provideExperimentalZonelessChangeDetection(),
      provideHttpClient()
    ]
  });

  const injector = appRef.injector;

  // Register all Maretha Angular components as native Web Components
  const elements: [string, any][] = [
    ['maretha-document-chat', MarethaDocumentChatComponent],
    ['maretha-document-interaction', MarethaDocumentInteractionComponent],
    ['maretha-settings', MarethaSettingsComponent],
    ['maretha-similar-documents', MarethaSimilarDocumentsComponent],
    ['maretha-embedding-search', MarethaEmbeddingSearchComponent],
  ];

  for (const [tag, component] of elements) {
    if (!customElements.get(tag)) {
      const el = createCustomElement(component, { injector });
      customElements.define(tag, el);
    }
  }

  console.log('[Maretha Angular Elements] All custom elements registered.');
})();
