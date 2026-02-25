import { Injectable } from '@angular/core';
// Nuxeo JS client is loaded globally by Nuxeo Web UI — no npm import needed
declare const Nuxeo: any;

@Injectable({ providedIn: 'root' })
export class NuxeoService {
    private client: any;

    /**
     * Lazily initialises and returns the Nuxeo JS client.
     * The client reuses the same browser session / cookies as the Nuxeo Web UI,
     * so no explicit login step is needed when this bundle runs inside
     * the Nuxeo Web UI context.
     */
    getClient(): any {
        if (!this.client) {
            this.client = new Nuxeo({
                baseURL: this.getNuxeoBaseUrl(),
            });
        }
        return this.client;
    }

    /** Resolves the Nuxeo server base URL from the current browser URL. */
    private getNuxeoBaseUrl(): string {
        const url = window.location.href;
        const match = url.match(/^(https?:\/\/[^/]+\/nuxeo)/i);
        return match ? match[1] : '/nuxeo';
    }

    /**
     * Execute a Nuxeo Automation operation.
     * @param  operationId  e.g. 'GetSettingsOperation'
     * @param  params       Operation params object
     * @param  input        Optional document or document UID string
     */
    executeOperation(operationId: string, params: Record<string, unknown> = {}, input?: any): Promise<any> {
        const op = this.getClient().operation(operationId);
        if (input !== undefined && input !== null) {
            op.input(input);
        }
        op.params(params);
        return op.execute();
    }
}
