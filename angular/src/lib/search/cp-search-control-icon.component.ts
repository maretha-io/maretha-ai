import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Decorative indicators; their surrounding native controls retain accessible names. */
@Component({
  selector: 'cp-search-control-icon', standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    @switch (kind()) {
      @case ('grid') { <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /> }
      @case ('table') { <rect x="3" y="4" width="18" height="16" /><path d="M3 9h18M3 14h18M9 4v16" /> }
      @case ('columns') { <rect x="3" y="4" width="18" height="16" /><path d="M9 4v16M15 4v16" /> }
      @case ('refresh') { <path d="M20 7v5h-5M19.4 12a7.4 7.4 0 1 0-1.5 4.5M20 12l-4-4" /> }
      @case ('ascending') { <path d="M6 20V4m-4 4 4-4 4 4M13 7h8M13 12h6M13 17h4" /> }
      @case ('descending') { <path d="M6 4v16m-4-4 4 4 4-4M13 7h8M13 12h6M13 17h4" /> }
      @case ('sort') { <path d="M4 6h16M4 12h11M4 18h6" /> }
      @case ('filter') { <path d="M3 5h18l-7 8v6l-4 2v-8z" /> }
      @case ('queue') { <path d="M3 6h12M3 12h9M3 18h12M17 9l4 3-4 3" /> }
    }
  </svg>`,
  styles: ':host{display:inline-flex;width:20px;height:20px;flex:0 0 20px;vertical-align:middle}svg{width:100%;height:100%;display:block}',
})
export class CpSearchControlIconComponent {
  readonly kind = input<'grid' | 'table' | 'columns' | 'refresh' | 'ascending' | 'descending' | 'sort' | 'filter' | 'queue'>('grid');
}
