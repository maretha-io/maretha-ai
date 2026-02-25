import { Component, Input, OnInit, ViewEncapsulation, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'maretha-embedding-search',
    standalone: true,
    imports: [CommonModule, FormsModule],
    encapsulation: ViewEncapsulation.ShadowDom,
    template: `
    <div class="search-form">
      <div class="field">
        <label>What are you looking for?</label>
        <input class="search-input" type="text" [(ngModel)]="vectorQuery" placeholder="Describe what you are looking for..." (keydown.enter)="emitSearch()" />
      </div>
      <div class="field">
        <label>Confidence: {{ minScore }}</label>
        <input type="range" min="0" max="1" step="0.1" [(ngModel)]="minScore" class="slider" (change)="emitSearch()" />
      </div>
      <div class="field">
        <label>Full-text search</label>
        <input class="search-input" type="text" [(ngModel)]="fullText" placeholder="Full text search..." (keydown.enter)="emitSearch()" />
      </div>
      <button class="search-btn" (click)="emitSearch()">Search</button>
    </div>
  `,
    styles: [`
    :host { display: block; }
    .search-form { display: flex; flex-direction: column; gap: .75rem; padding: .5rem 0; }
    .field { display: flex; flex-direction: column; gap: .25rem; }
    .field label { font-size: .8rem; color: #666; }
    .search-input {
      padding: .5rem .75rem; border: 1px solid #ccc;
      border-radius: .25rem; font-size: .9rem; outline: none;
    }
    .search-input:focus { border-color: var(--nuxeo-button-primary, #5cb85c); }
    .slider { width: 100%; accent-color: var(--nuxeo-button-primary, #5cb85c); }
    .search-btn {
      background: var(--nuxeo-button-primary, #5cb85c);
      color: white; border: none; border-radius: .25rem;
      padding: .5rem 1rem; cursor: pointer; font-size: .9rem; align-self: flex-start;
    }
  `]
})
export class MarethaEmbeddingSearchComponent implements OnInit {
    @Input('vector-query') initialVectorQuery: string = '';
    @Input('min-score') initialMinScore: number = 0.8;
    @Output() paramsChange = new EventEmitter<Record<string, unknown>>();

    vectorQuery = '';
    minScore = 0.8;
    fullText = '';

    ngOnInit() {
        this.vectorQuery = this.initialVectorQuery || '';
        this.minScore = Number(this.initialMinScore) || 0.8;
    }

    emitSearch() {
        const params: Record<string, unknown> = {
            vectorQuery: this.vectorQuery,
            min_score: this.minScore,
            vectorField: 'text',
        };
        if (this.fullText) {
            params['ecm_fulltext'] = `${this.fullText}*`;
        }
        this.paramsChange.emit(params);
        // Also dispatch a DOM event for the Polymer shell to intercept
        const el = document.querySelector('nuxeo-app');
        if (el) {
            el.dispatchEvent(new CustomEvent('nuxeo-page-navigation', { detail: { page: 'search' }, bubbles: true, composed: true }));
        }
    }
}
