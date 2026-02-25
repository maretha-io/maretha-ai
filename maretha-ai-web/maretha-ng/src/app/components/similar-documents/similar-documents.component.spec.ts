import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarethaSimilarDocumentsComponent } from './similar-documents.component';
import { NuxeoService } from '../../nuxeo.service';
import { SimpleChange } from '@angular/core';

describe('MarethaSimilarDocumentsComponent', () => {
    let component: MarethaSimilarDocumentsComponent;
    let fixture: ComponentFixture<MarethaSimilarDocumentsComponent>;
    let nuxeoSpy: jasmine.SpyObj<NuxeoService>;

    const mockEntries = [
        { uid: 'uid-1', title: 'Report Q1', type: 'File', contextParameters: { relevance_score: 0.91 } },
        { uid: 'uid-2', title: 'Annual Review', type: 'File', contextParameters: { relevance_score: 0.85 } }
    ];

    beforeEach(async () => {
        nuxeoSpy = jasmine.createSpyObj('NuxeoService', ['executeOperation']);
        nuxeoSpy.executeOperation.and.returnValue(Promise.resolve({ entries: mockEntries }));

        await TestBed.configureTestingModule({
            imports: [MarethaSimilarDocumentsComponent],
            providers: [{ provide: NuxeoService, useValue: nuxeoSpy }]
        }).compileComponents();

        fixture = TestBed.createComponent(MarethaSimilarDocumentsComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have empty documents list initially', () => {
        expect(component.similarDocuments).toEqual([]);
        expect(component.loading).toBeFalse();
    });

    it('should load similar documents when documentId changes', async () => {
        component.documentId = 'source-uid';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'source-uid', true) });
        await fixture.whenStable();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith('GetSimilarDocumentsOperation', {}, 'source-uid');
        expect(component.similarDocuments.length).toBe(2);
        expect(component.similarDocuments[0].title).toBe('Report Q1');
        expect(component.similarDocuments[0].score).toBe(0.91);
    });

    it('should load similar documents when document @Input changes', async () => {
        component.document = { uid: 'doc-uid-2' };
        component.ngOnChanges({ document: new SimpleChange(null, { uid: 'doc-uid-2' }, true) });
        await fixture.whenStable();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith('GetSimilarDocumentsOperation', {}, 'doc-uid-2');
    });

    it('should set loading=false after data loads', async () => {
        component.documentId = 'uid';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'uid', true) });
        await new Promise<void>(resolve => setTimeout(resolve, 10));
        await fixture.whenStable();

        expect(component.loading).toBeFalse();
    });

    it('getDocUrl() should return correct Nuxeo UI link', () => {
        const url = component.getDocUrl('some-uid');
        expect(url).toContain('/doc/some-uid');
    });

    it('should handle errors gracefully', async () => {
        nuxeoSpy.executeOperation.and.returnValue(Promise.reject(new Error('Not found')));
        component.documentId = 'uid';
        component.ngOnChanges({ documentId: new SimpleChange(null, 'uid', true) });
        await new Promise<void>(resolve => setTimeout(resolve, 10));
        await fixture.whenStable();

        expect(component.loading).toBeFalse();
        expect(component.similarDocuments).toEqual([]);
    });
});
