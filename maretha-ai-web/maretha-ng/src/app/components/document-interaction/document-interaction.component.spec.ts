import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarethaDocumentInteractionComponent } from './document-interaction.component';
import { NuxeoService } from '../../nuxeo.service';
import { SimpleChange } from '@angular/core';

const mockInteractions = [
    { id: 'int-1', name: 'Summarise', description: 'Summarise document', parameters: [{ name: 'Language', type: 'string', value: 'en' }], saveMetadata: false },
    { id: 'int-2', name: 'Translate', description: 'Translate document', parameters: [], saveMetadata: true }
];

describe('MarethaDocumentInteractionComponent', () => {
    let component: MarethaDocumentInteractionComponent;
    let fixture: ComponentFixture<MarethaDocumentInteractionComponent>;
    let nuxeoSpy: jasmine.SpyObj<NuxeoService>;

    beforeEach(async () => {
        nuxeoSpy = jasmine.createSpyObj('NuxeoService', ['executeOperation']);
        nuxeoSpy.executeOperation.and.returnValue(Promise.resolve({ value: mockInteractions }));

        await TestBed.configureTestingModule({
            imports: [MarethaDocumentInteractionComponent],
            providers: [{ provide: NuxeoService, useValue: nuxeoSpy }]
        }).compileComponents();

        fixture = TestBed.createComponent(MarethaDocumentInteractionComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should start with empty state', () => {
        expect(component.interactions).toEqual([]);
        expect(component.selectedIndex).toBe(-1);
        expect(component.selectedInteraction).toBeNull();
    });

    it('should load interactions when document changes', async () => {
        component.documentId = 'doc-uid-1';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'doc-uid-1', true) });
        await fixture.whenStable();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith('GetInteractionsForDocumentOperation', {}, 'doc-uid-1');
        expect(component.interactions.length).toBe(2);
        expect(component.selectedIndex).toBe(0);
        expect(component.selectedInteraction).toEqual(mockInteractions[0]);
    });

    it('should reset on document change before reloading', async () => {
        component.documentId = 'doc-uid-1';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'doc-uid-1', true) });
        await fixture.whenStable();

        // Now change to different document
        component.documentId = 'doc-uid-2';
        component.ngOnChanges({ documentId: new SimpleChange('doc-uid-1', 'doc-uid-2', false) });

        // Immediately after ngOnChanges, reset should have cleared state
        expect(component.interactionContext).toEqual([]);
    });

    it('should call RunInteractionOperation on interactWithDocument()', async () => {
        nuxeoSpy.executeOperation.and.callFake((op: string) => {
            if (op === 'GetInteractionsForDocumentOperation') return Promise.resolve({ value: mockInteractions });
            return Promise.resolve({ value: 'Summary text' });
        });
        component.documentId = 'doc-uid-1';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'doc-uid-1', true) });
        await fixture.whenStable();

        component.interactWithDocument();
        expect(component.loading).toBeTrue();
        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith(
            'RunInteractionOperation',
            jasmine.objectContaining({ interactionId: 'int-1' }),
            'doc-uid-1'
        );
    });

    it('should handle run errors gracefully', async () => {
        nuxeoSpy.executeOperation.and.callFake((op: string) => {
            if (op === 'GetInteractionsForDocumentOperation') return Promise.resolve({ value: mockInteractions });
            return Promise.reject(new Error('Server error'));
        });
        component.documentId = 'doc-uid-1';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'doc-uid-1', true) });
        await fixture.whenStable();

        component.interactWithDocument();
        await new Promise<void>(resolve => setTimeout(resolve, 10));
        await fixture.whenStable();

        expect(component.loading).toBeFalse();
        const lastMsg = component.interactionContext[component.interactionContext.length - 1];
        expect(lastMsg.content).toContain('Error');
    });

    it('onInteractionSelected() should update selectedInteraction', async () => {
        component.documentId = 'uid';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'uid', true) });
        await fixture.whenStable();

        component.selectedIndex = 1;
        component.onInteractionSelected();
        expect(component.selectedInteraction).toEqual(mockInteractions[1]);
        expect(component.interactionContext).toEqual([]);
    });
});
