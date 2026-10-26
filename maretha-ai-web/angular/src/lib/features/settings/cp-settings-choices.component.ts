import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Compact, keyboard-accessible multiple choice editor used by management rows. */
@Component({
  selector: 'cp-settings-choices', standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<details #menu (keydown.escape)="menu.open = false">
    <summary [attr.aria-label]="label()" [attr.aria-disabled]="disabled()" (click)="disabled() && $event.preventDefault()">
      @for (value of values(); track value) { <span class="chip">{{ title(value) }}</span> }
      @if (!values().length) { <span class="placeholder">Select {{ label() }}</span> }
      <span class="arrow" aria-hidden="true"></span>
    </summary>
    <div class="options" [attr.aria-label]="label()">
      @for (choice of available(); track choice.id) {
        <label><input type="checkbox" [checked]="values().includes(choice.id)" [disabled]="disabled()"
          (change)="toggle(choice.id, $any($event.target).checked)" />{{ choice.label }}</label>
      }
    </div>
  </details>`,
  styles: `:host{display:block;min-width:0}details{position:relative}summary{display:flex;align-items:center;gap:4px;flex-wrap:wrap;min-height:32px;padding:4px 18px 4px 0;border-bottom:1px solid var(--nuxeo-text-default,#454552);cursor:pointer;list-style:none;box-sizing:border-box}summary::-webkit-details-marker{display:none}.chip{padding:3px 7px;border-radius:12px;background:var(--nuxeo-pill-background,#eee);font-size:12px;line-height:1.3;overflow-wrap:anywhere}.arrow{position:absolute;right:4px;top:15px;border:4px solid transparent;border-top-color:currentColor}.placeholder{color:#999}.options{position:absolute;top:100%;left:0;z-index:30;min-width:100%;width:max-content;max-width:min(32rem,80vw);max-height:260px;overflow:auto;padding:8px;background:var(--nuxeo-box,#fff);border:1px solid var(--nuxeo-border,#ddd);box-shadow:0 3px 12px #0002;box-sizing:border-box}.options label{display:flex;align-items:center;gap:8px;padding:7px;cursor:pointer;white-space:normal}.options label:hover{background:var(--nuxeo-pill-background,#f3f3f5)}input{accent-color:var(--nuxeo-primary-color,#006bff)}summary:focus-visible,input:focus-visible{outline:2px solid var(--nuxeo-primary-color,#006bff);outline-offset:2px}`,
})
export class CpSettingsChoicesComponent {
  readonly label = input.required<string>();
  readonly choices = input<readonly (string | { id: string; label: string })[]>([]);
  readonly values = input<readonly string[]>([]);
  readonly disabled = input(false);
  readonly valuesChange = output<string[]>();
  readonly available = computed(() => this.choices().map(choice => typeof choice === 'string' ? { id: choice, label: choice } : choice));
  title(value: string): string { return this.available().find(choice => choice.id === value)?.label ?? value; }
  toggle(value: string, checked: boolean): void {
    if (this.disabled()) return;
    this.valuesChange.emit(checked ? [...new Set([...this.values(), value])] : this.values().filter(item => item !== value));
  }
}
