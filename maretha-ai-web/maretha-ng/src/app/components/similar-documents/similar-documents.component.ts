import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NuxeoService } from '../../nuxeo.service';

interface SimilarDocument {
    uid: string;
    title: string;
    type: string;
    score?: number;
    thumbnailUrl?: string;
}

@Component({
    selector: 'maretha-similar-documents',
    standalone: true,
    imports: [CommonModule],
    encapsulation: ViewEncapsulation.ShadowDom,
    template: `
    <div class="similar-docs-wrapper">
      <div *ngIf="loading" class="loading">Searching similar documents...</div>

      <div *ngIf="!loading && similarDocuments.length > 0" class="doc-grid">
        <a *ngFor="let doc of similarDocuments" class="doc-card" [href]="getDocUrl(doc.uid)" target="_top">
          <div class="doc-thumb" [style.backgroundImage]="doc.thumbnailUrl ? 'url(' + doc.thumbnailUrl + ')' : 'none'">
            <span *ngIf="!doc.thumbnailUrl" class="doc-icon">📄</span>
          </div>
          <div class="doc-info">
            <span class="doc-title" [title]="doc.title">{{ doc.title }}</span>
            <span *ngIf="doc.score" class="doc-score">{{ (doc.score * 100).toFixed(0) }}% similar</span>
          </div>
        </a>
      </div>

      <div *ngIf="!loading && similarDocuments.length === 0" class="empty-state">
        No similar documents found.
      </div>
    </div>
  `,
    styles: [`
    :host { display: block; }
    .similar-docs-wrapper { padding: .5rem 0; }
    .loading { color: #888; font-style: italic; font-size: .9rem; padding: .5rem 0; }
    .doc-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: .75rem;
    }
    .doc-card {
      display: flex; flex-direction: column;
      border: 1px solid #e0e0e0; border-radius: .25rem;
      overflow: hidden; text-decoration: none; color: inherit;
      transition: box-shadow .15s;
    }
    .doc-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.12); }
    .doc-thumb {
      height: 80px; background-size: cover; background-position: center;
      background-color: #f5f5f5; display: flex; align-items: center; justify-content: center;
    }
    .doc-icon { font-size: 2rem; }
    .doc-info { padding: .4rem; display: flex; flex-direction: column; gap: .15rem; }
    .doc-title { font-size: .8rem; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .doc-score { font-size: .7rem; color: var(--nuxeo-button-primary, #5cb85c); }
    .empty-state { color: #aaa; font-size: .9rem; padding: .5rem 0; }
  `]
})
export class MarethaSimilarDocumentsComponent implements OnChanges {
    @Input('document-id') documentId: string = '';
    @Input() document: any;

    similarDocuments: SimilarDocument[] = [];
    loading = false;

    constructor(private nuxeo: NuxeoService) { }

    ngOnChanges(changes: SimpleChanges) {
        if ((changes['document'] || changes['documentId']) && (this.document?.uid || this.documentId)) {
            this.loadSimilarDocuments();
        }
    }

    loadSimilarDocuments() {
        const docInput = this.document?.uid || this.documentId;
        if (!docInput) return;

        this.loading = true;
        this.similarDocuments = [];

        this.nuxeo.executeOperation('GetSimilarDocumentsOperation', {}, docInput)
            .then((response: any) => {
                this.similarDocuments = (response.entries || response.value || []).map((entry: any) => ({
                    uid: entry.uid,
                    title: entry.title || entry.properties?.['dc:title'] || 'Untitled',
                    type: entry.type,
                    score: entry.contextParameters?.relevance_score,
                    thumbnailUrl: entry.contextParameters?.thumbnail?.url
                }));
            })
            .catch((err: any) => console.error('[MarethaSimilarDocs] Error:', err))
            .finally(() => { this.loading = false; });
    }

    getDocUrl(uid: string): string {
        const baseUrl = window.location.href.split('/nuxeo/')[0];
        return `${baseUrl}/nuxeo/ui/#!/doc/${uid}`;
    }
}
