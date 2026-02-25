import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarethaSettingsComponent } from './settings.component';
import { NuxeoService } from '../../nuxeo.service';

describe('MarethaSettingsComponent', () => {
    let component: MarethaSettingsComponent;
    let fixture: ComponentFixture<MarethaSettingsComponent>;
    let nuxeoSpy: jasmine.SpyObj<NuxeoService>;

    const mockSettings = {
        interactions: [{ id: 'i1', name: 'Summarise' }],
        eventsSettings: [{ eventId: 'documentCreated', enabled: true, interactionId: 'i1' }],
        aiSettings: { model: 'gpt-4o', baseUrl: 'https://api.openai.com', apiKey: '' },
        esSettings: { indexName: 'nuxeo', embeddingDimensions: 1536 },
        awsS3AccessEnabled: false
    };

    beforeEach(async () => {
        nuxeoSpy = jasmine.createSpyObj('NuxeoService', ['executeOperation']);
        nuxeoSpy.executeOperation.and.callFake((op: string) => {
            if (op === 'GetSettingsOperation') return Promise.resolve({ value: mockSettings });
            if (op === 'GetDocumentTypesOperation') return Promise.resolve({ value: [{ id: 'File', label: 'File' }] });
            if (op === 'SaveInteractionsOperation') return Promise.resolve({ value: 'ok' });
            return Promise.resolve({});
        });

        await TestBed.configureTestingModule({
            imports: [MarethaSettingsComponent],
            providers: [{ provide: NuxeoService, useValue: nuxeoSpy }]
        }).compileComponents();

        fixture = TestBed.createComponent(MarethaSettingsComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should start on the interactions tab', () => {
        expect(component.selectedTab).toBe('interactions');
    });

    it('should load settings on init', async () => {
        component.ngOnInit();
        await fixture.whenStable();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith('GetSettingsOperation', {});
        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith('GetDocumentTypesOperation', {});
        expect(component.interactions.length).toBe(1);
        expect(component.aiSettings.model).toBe('gpt-4o');
    });

    it('selectTab() should switch the active tab', () => {
        component.selectTab('settings');
        expect(component.selectedTab).toBe('settings');
        component.selectTab('events');
        expect(component.selectedTab).toBe('events');
    });

    it('markDirty() should set isDirty to true', () => {
        expect(component.isDirty).toBeFalse();
        component.markDirty('interactions');
        expect(component.isDirty).toBeTrue();
    });

    it('saveChanges() should call SaveInteractionsOperation', async () => {
        component.ngOnInit();
        await fixture.whenStable();
        spyOn(window, 'alert'); // suppress alert dialog in tests

        component.markDirty('aiSettings');
        component.saveChanges();
        await fixture.whenStable();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith(
            'SaveInteractionsOperation',
            jasmine.any(Object)
        );
        expect(component.isDirty).toBeFalse();
    });

    it('should reset isDirty after successful save', async () => {
        component.ngOnInit();
        await fixture.whenStable();
        spyOn(window, 'alert');

        component.isDirty = true;
        component.saveChanges();
        await fixture.whenStable();

        expect(component.isDirty).toBeFalse();
    });
});
