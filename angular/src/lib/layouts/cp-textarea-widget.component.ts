import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SchemaFieldComponent, type WidgetProps } from '@maretha/webui-sdk';

let nextId = 0;
@Component({
  selector: 'cp-textarea-widget', standalone: true, imports: [SchemaFieldComponent],
  template: `@if (props().field.multiple || props().field.type !== 'string') {
      <nx-schema-field [props]="props()" />
    } @else { <label [for]="id">{{ props().field.label }}</label>
    <textarea [id]="id" rows="4" [value]="text()" [disabled]="props().disabled"
      [required]="!!props().field.required" [attr.maxlength]="props().field.maxLength ?? null"
      [attr.aria-invalid]="props().error ? true : null" [attr.aria-describedby]="props().error ? id + '-error' : null"
      (input)="commit($any($event.target).value)"></textarea>
    @if (props().error) { <span [id]="id + '-error'" role="alert">{{ props().error }}</span> } }`,
  styles: ':host{display:flex;flex-direction:column;gap:6px}textarea{width:100%;padding:8px;resize:vertical;background:var(--nuxeo-box,#fff);color:inherit;border:1px solid var(--nuxeo-border,#bbb)}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CpTextareaWidgetComponent {
  readonly props = input.required<WidgetProps>();
  readonly id = `cp-textarea-${++nextId}`;
  text(): string { const value = this.props().value; return value == null ? '' : typeof value === 'string' ? value : JSON.stringify(value); }
  commit(value: string): void { if (!this.props().disabled) this.props().commit(value); }
}
