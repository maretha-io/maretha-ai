#!/usr/bin/env python3
"""Render the recorded browser evidence; never infer a pass from a screenshot."""
from pathlib import Path
import html
import json
import re

ROOT = Path(__file__).resolve().parent
esc = lambda value: html.escape(str(value))
records = {p.stem: json.loads(p.read_text()) for p in (ROOT / 'evidence').glob('*audit.json')}
core = records.get('core-audit', {})
validation_path = ROOT / 'evidence' / 'core-final-validation.json'
validation = json.loads(validation_path.read_text()) if validation_path.exists() else {}


def detail(value):
    """Preserve structured evidence without treating a pending field as a pass."""
    if isinstance(value, (dict, list)):
        return '<pre>' + esc(json.dumps(value, ensure_ascii=False, indent=2)) + '</pre>'
    return esc(value)


images = sorted((ROOT / 'screenshots').glob('*.jpg'))
groups = {}
for image in images:
    name = image.stem
    side = 'Baseline /ui' if re.search(r'-(ui|polymer)-', name) else 'Angular /ng'
    phase = ('After' if name.endswith('-after') else
             'Before' if name.endswith('-before') else 'Phase unspecified')
    key = re.sub(r'-(ui|ng|polymer|angular)-', '-', name)
    key = re.sub(r'-(before|after)$', '', key)
    groups.setdefault(key, []).append((side, phase, image))

rows = []
for scope, data in records.items():
    for check in [*data.get('coverage', data.get('checks', [])), *data.get('findings', [])]:
        title = check.get('area', check.get('surface', check.get('id', scope)))
        description = check.get('observed', check.get('before', check.get('finding', '')))
        actions = check.get('checked', check.get('actions', []))
        result = check.get('afterDeploymentVerification', check.get('result', check.get('outcome', check.get('status', data.get('status', 'Verification not recorded')))))
        fix = check.get('sourceFix', check.get('sourceChange', check.get('fix', '')))
        limit = check.get('notExecuted', '')
        rows.append('<tr><td>' + esc(scope.replace('-audit', '')) + '</td><td><strong>' + esc(title)
                    + '</strong><p>' + esc(description) + '</p><p>' + esc('; '.join(actions))
                    + '</p></td><td>' + esc(fix) + '<p>' + esc(result) + '</p><small>'
                    + esc(limit) + '</small></td></tr>')

panels = []
for key, pictures in groups.items():
    cards = []
    for side, phase, image in sorted(pictures, key=lambda p: (p[1] != 'After', p[0] != 'Baseline /ui')):
        src = 'screenshots/' + image.name
        cards.append(f'<figure><a href="{src}" target="_blank"><img loading="lazy" src="{src}" alt="{esc(key)} — {side} {phase}"></a><figcaption>{side} · {phase} · {esc(image.name)}</figcaption></figure>')
    panels.append(f'<section class="comparison" data-search="{esc(key)}"><h3>{esc(key.replace("-", " "))}</h3><div class="pair">{"".join(cards)}</div></section>')

test_rows = ''.join('<tr><th>' + esc(check.get('name', 'Check')) + '</th><td>'
                    + detail(check.get('result', 'Not recorded')) + '</td></tr>'
                    for check in core.get('checks', []))
validation_details = ''.join('<details><summary>' + esc(label) + '</summary>'
                             + detail(validation[key]) + '</details>'
                             for key, label in [
                                 ('angularTests', 'Complete Angular suite'),
                                 ('typecheck', 'TypeScript'), ('lint', 'ESLint scope and limitation'),
                                 ('i18n', 'Localization and linguistic review'),
                                 ('sdk', 'SDK consumer, API and identities'),
                                 ('releaseAssemblyTests', 'Release assembly guards'),
                                 ('budgetOptimization', 'Initial bundle budget'),
                             ] if key in validation)
provenance_rows = ''.join('<tr><th>' + esc(label) + '</th><td>' + detail(value) + '</td></tr>'
                          for label, value in [
                              ('Observed runtime before changes', core.get('runtimeBefore', {})),
                              ('Final release versions', core.get('target', validation.get('versions', {}))),
                              ('Source revisions', core.get('sourceRevisions', core.get('source', 'Not yet recorded'))),
                              ('Deployment', core.get('deployment', 'Pending — no deployed success inferred from source tests')),
                              ('Final source checks', validation.get('verifiedAt', 'Not recorded')),
                              ('Earlier deployments', core.get('deploymentHistory', [])),
                              ('Fixture cleanup', core.get('fixtures', {}).get('cleanupVerification', {})),
                              ('Post-deployment checks', core.get('afterDeploymentVerification', validation.get('afterDeployment', 'Pending'))),
                          ])
scope_limits = ''.join('<details><summary>' + esc(scope.replace('-audit', ''))
                      + ' recorded limitations / remaining checks</summary>'
                      + detail(data.get('limitations', data.get('remaining', []))) + '</details>'
                      for scope, data in records.items()
                      if data.get('limitations') or data.get('remaining'))
residual_details = ''.join('<details><summary>' + esc(scope.replace('-audit', ''))
                          + ' final checks and visual differences</summary>'
                          + detail({key: data[key] for key in ['finalChecks', 'residualDifferences', 'finalInteractionResults'] if key in data})
                          + '</details>' for scope, data in records.items()
                          if any(key in data for key in ['finalChecks', 'residualDifferences', 'finalInteractionResults']))
evidence_links = ''.join(f'<li><a href="evidence/{esc(p.name)}">{esc(p.name)}</a></li>'
                         for p in sorted((ROOT / 'evidence').iterdir())
                         if p.is_file() and p.suffix in {'.json', '.log', '.txt'})
result = f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Demo UI parity re-audit · 26 October 2026</title>
<style>
:root{{font:15px/1.6 system-ui,sans-serif;color:#1f2d3d;background:#f4f6f9}}body{{margin:0}}header,main{{max-width:1600px;margin:auto;padding:28px}}header{{background:#17243b;color:white}}h1{{font-size:30px;margin:0}}h2{{margin-top:36px}}h3{{font-size:18px}}a{{color:#0655b5}}header a{{color:#a7d0ff}}.status{{background:#fff3c7;color:#614700;padding:14px;border-radius:6px}}.pair{{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:16px}}figure{{margin:0;background:white;border:1px solid #dbe0e8}}img{{width:100%;height:auto;display:block}}figcaption{{padding:10px;font-size:12px;overflow-wrap:anywhere}}table{{border-collapse:collapse;background:white;width:100%}}td,th{{border:1px solid #dbe0e8;padding:12px;text-align:left;vertical-align:top}}td p{{margin:5px 0}}small{{color:#735800}}input{{padding:12px;width:min(600px,90%);border:1px solid #aab7c7;border-radius:5px}}.comparison{{margin:26px 0}}code{{background:#e5eaf1;padding:2px 5px}}.muted{{color:#546272}}pre{{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}}details{{margin:12px 0}}summary{{cursor:pointer}}
</style>
<header><h1>Demo /ui vs /ng parity re-audit</h1><p>26 October 2026 · stock Nuxeo Web UI and io.maretha.cp · Chrome browser evidence</p><a href="../angular-parity-2026-10-24/report.html">Previous audit</a></header>
<main><p class="status">{esc(core.get('status', 'Verification pending'))}</p>
<p>Baseline is Nuxeo Web UI. The Angular core and CP Angular layouts are the implementation targets. Before captures came from Web UI 2025.21.1, Angular 3.21.3 and CP 1.0.29, on backend 2025.20-SNAPSHOT.</p>
<p>Final release targets: Web UI {esc(core.get('target',{}).get('webui'))}, Angular {esc(core.get('target',{}).get('angular'))}, SDK {esc(core.get('target',{}).get('sdk'))} and CP {esc(core.get('target',{}).get('cp'))}. Source tests do not establish deployed parity; the final record below tracks runtime verification separately.</p>
<h2>Scope and limits</h2><p>Available navigation, account/admin pages, document views, search, folders, actions, workflows and CP layouts are covered by the action records below. Writes use disposable audit fixtures. No global AI settings, real users, credentials or existing documents were edited. Actions requiring external accounts, other roles, unavailable content types or unrelated add-ons are not claimed as tested.</p>
<p>Unrelated demo add-ons are unavailable in Angular for this scope: SA Inventory/Requests/MSA work queue, SA Accessibility, linked work tasks, media conversion, DLP controls, template rendering, portal and other optional extensions. CP is included. Baseline PDF/search failures were traced to malformed asset paths from linked build dependencies and received a separate packaging correction.</p>
{scope_limits}{residual_details}
<p>Accepted visual variation includes small typography, spacing and action-placement differences. Unexecuted writes and backend failures are identified explicitly in the action records and evidence.</p>
<h2>Recorded checks and fixes</h2><table><thead><tr><th>Scope</th><th>Browser evidence</th><th>Fix / verification / limitation</th></tr></thead><tbody>{''.join(rows)}</tbody></table>
<h2>Tests and build checks</h2><p>These checks describe source and build verification. Deployed behavior is recorded separately below.</p><table><tbody>{test_rows}</tbody></table>{validation_details}
<h2>Source and deployment provenance</h2><table><tbody>{provenance_rows}</tbody></table>
<h2>Screenshot comparisons</h2><p>{len(images)} captures. Click a screenshot for full resolution. After captures span the initial patch (Web UI 2025.21.2 / Angular 3.21.4 / CP 1.0.30), the cache/workflow/CP follow-up (2025.21.3/3.21.5/1.0.31), PDF readiness (3.21.6/1.0.32), and the final metadata resize/task-card patch (3.21.7/1.0.33). Exact per-scope capture versions are preserved in the evidence; a filename containing final does not imply the latest release. Captures show the actual live state, including baseline errors and pending loads; differing data or viewport state is not a pixel-parity claim.</p><input id="filter" type="search" aria-label="Filter screenshots" placeholder="Filter: folder, picture, settings, account…">
{''.join(panels)}
<h2>Machine-readable evidence</h2><ul>{evidence_links}</ul><p class="muted">Fixture cleanup: {esc(core.get('fixtures',{}).get('cleanup','Pending'))}. See evidence records for exact actions, test results, capture conditions and deployment provenance.</p></main>
<script>document.getElementById('filter').addEventListener('input',e=>{{for(const p of document.querySelectorAll('.comparison'))p.hidden=!p.dataset.search.includes(e.target.value.toLowerCase())}})</script></html>"""
(ROOT / 'report.html').write_text(result)
print(f'Rendered report with {len(rows)} coverage rows and {len(images)} screenshots.')
