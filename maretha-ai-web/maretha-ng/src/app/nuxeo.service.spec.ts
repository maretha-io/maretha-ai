import { TestBed } from '@angular/core/testing';
import { NuxeoService } from './nuxeo.service';

// Stub for the Nuxeo client – we don't want real HTTP calls in unit tests
const mockOperation = {
    input: jasmine.createSpy('input').and.returnValue({
        params: jasmine.createSpy('params').and.returnValue({
            execute: jasmine.createSpy('execute').and.returnValue(Promise.resolve({ value: 'test-result' }))
        })
    }),
    params: jasmine.createSpy('params').and.callFake(function () { return mockOperation; }),
    execute: jasmine.createSpy('execute').and.returnValue(Promise.resolve({ value: 'test-result' }))
};

const mockNuxeoClientInstance = {
    operation: jasmine.createSpy('operation').and.returnValue(mockOperation)
};

// Patch the global Nuxeo constructor before any test
(window as any).Nuxeo = jasmine.createSpy('Nuxeo').and.returnValue(mockNuxeoClientInstance);

describe('NuxeoService', () => {
    let service: NuxeoService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(NuxeoService);
        // Reset spies
        mockNuxeoClientInstance.operation.calls.reset();
        mockOperation.execute.calls.reset();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('getClient() should return a Nuxeo client instance', () => {
        const client = service.getClient();
        expect(client).toBe(mockNuxeoClientInstance);
    });

    it('getClient() should reuse the same client on subsequent calls', () => {
        const c1 = service.getClient();
        const c2 = service.getClient();
        expect(c1).toBe(c2);
    });

    it('executeOperation() should call operation with correct id', async () => {
        await service.executeOperation('TestOperation', { foo: 'bar' });
        expect(mockNuxeoClientInstance.operation).toHaveBeenCalledWith('TestOperation');
    });
});
