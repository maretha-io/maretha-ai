import { ChangeDetectionStrategy, Component, SecurityContext, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { responsePresentation } from './interaction-models';

@Component({
  selector: 'cp-response', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (presentation().html !== null) { <div class="rich-response" [innerHTML]="presentation().html"></div> } @else { <pre>{{ presentation().text }}</pre> }`,
  styles: `:host{display:block;min-width:0;font:inherit;line-height:1.5}pre{font:inherit;line-height:1.5;white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.rich-response{overflow-wrap:anywhere}.rich-response :first-child{margin-top:0}.rich-response :last-child{margin-bottom:0}`,
})
export class CpResponseComponent {
  readonly value = input<unknown>('');
  private readonly sanitizer = inject(DomSanitizer);
  readonly presentation = computed(() => responsePresentation(this.value(), html => this.sanitizer.sanitize(SecurityContext.HTML, html)));
}
