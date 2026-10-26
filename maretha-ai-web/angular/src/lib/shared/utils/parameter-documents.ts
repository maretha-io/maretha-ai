import { catchError, forkJoin, map, of, type Observable } from 'rxjs';
import type { CpDocument } from '../types/cp-models';
import type { CpParameter } from '../types/interaction-models';

/** Resolve stored document selections for display, retaining inaccessible IDs. */
export function parameterDocumentTitles(parameters: readonly CpParameter[], getDocument: (uid: string) => Observable<CpDocument>): Observable<Record<string, string>> {
  const ids = [...new Set(parameters.flatMap(parameter => parameter.selectedDocumentIds ?? []))];
  if (!ids.length) return of({});
  return forkJoin(ids.map(uid => getDocument(uid).pipe(
    map(document => [uid, document.title || document.properties?.['dc:title'] || uid] as const),
    catchError(() => of([uid, uid] as const)),
  ))).pipe(map(entries => Object.fromEntries(entries)));
}
