import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { WebUiAppState } from '@maretha/webui-sdk';
import { Observable, map, throwError } from 'rxjs';
import { CpDocument, CpList, unwrapCpResponse } from './cp-models';

/** All requests use the host's HttpClient and its Nuxeo-scoped authentication. */
@Injectable({ providedIn: 'root' })
export class CpApiService {
  private readonly http = inject(HttpClient);
  private readonly app = inject(WebUiAppState);
  readonly nuxeoUrl = this.app.nuxeoUrl.replace(/\/+$/, '');
  readonly user = this.app.user;
  private readonly api = `${this.nuxeoUrl}/api/v1`;
  private readonly headers = { properties: '*', 'enrichers-document': 'permissions,thumbnail' };

  execute<T>(operationId: string, params: Readonly<Record<string, unknown>> = {}, input?: string | CpDocument): Observable<T> {
    const documentInput = typeof input === 'string' ? input : input?.uid;
    return this.http.post<unknown>(`${this.api}/automation/${encodeURIComponent(operationId)}`, {
      params,
      ...(documentInput === undefined ? {} : { input: documentInput }),
    }, { headers: { ...this.headers, 'Content-Type': 'application/json', Accept: 'application/json' } }).pipe(map(unwrapCpResponse<T>));
  }

  getDocument(uidOrPath: string): Observable<CpDocument> {
    const target = uidOrPath.startsWith('/')
      ? `path${uidOrPath.split('/').map(encodeURIComponent).join('/')}`
      : `id/${encodeURIComponent(uidOrPath)}`;
    return this.http.get<CpDocument>(`${this.api}/${target}`, { headers: this.headers });
  }

  query(nxql: string): Observable<CpList<CpDocument>> {
    return this.http.get<CpList<CpDocument>>(`${this.api}/query`, {
      params: { query: nxql }, headers: this.headers,
    });
  }

  provider(name: string, params: Readonly<Record<string, unknown>> = {}, method: 'GET' | 'POST' = 'GET'): Observable<CpList<CpDocument>> {
    // Nuxeo Elements uses Repository.PageProvider for POST queries. Large vectors must
    // travel in its JSON body rather than exceed the servlet/proxy URL limit.
    if (method === 'POST') {
      const pagingKeys = new Set(['currentPageIndex', 'pageSize', 'sortBy', 'sortOrder', 'quickFilters', 'queryParams']);
      const paging = Object.fromEntries(Object.entries(params).filter(([key]) => pagingKeys.has(key)));
      const namedParameters = Object.fromEntries(Object.entries(params).filter(([key, value]) => !pagingKeys.has(key) && value !== undefined && value !== null)
        .map(([key, value]) => [key, Array.isArray(value) ? JSON.stringify(value) : String(value)]));
      return this.http.post<unknown>(`${this.api}/automation/Repository.PageProvider`, {
        params: { providerName: name, ...paging, namedParameters },
      }, { headers: { ...this.headers, 'Content-Type': 'application/json', Accept: 'application/json',
        'enrichers-document': 'thumbnail,permissions,highlight,relevance_score', 'fetch-aggregate': 'key',
        'fetch-document': 'properties', 'translate-directoryEntry': 'label' } }).pipe(map(unwrapCpResponse<CpList<CpDocument>>));
    }
    let query = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value === undefined || value === null) continue;
      if (Array.isArray(value)) for (const item of value) query = query.append(key, String(item));
      else query = query.set(key, String(value));
    }
    return this.http.get<CpList<CpDocument>>(`${this.api}/search/pp/${encodeURIComponent(name)}/execute`, {
      params: query, headers: this.headers,
    });
  }

  saveDocument(document: CpDocument, properties: Readonly<Record<string, unknown>>): Observable<CpDocument> {
    if (!document.changeToken) return throwError(() => new Error('Reload the document before saving: its change token is missing.'));
    return this.http.put<CpDocument>(`${this.api}/id/${encodeURIComponent(document.uid)}`, {
      'entity-type': 'document', uid: document.uid, changeToken: document.changeToken, properties,
    }, { headers: this.headers });
  }
}
