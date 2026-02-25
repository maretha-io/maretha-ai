import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MarethaDocumentChatComponent } from './document-chat.component';
import { NuxeoService } from '../../nuxeo.service';

describe('MarethaDocumentChatComponent', () => {
    let component: MarethaDocumentChatComponent;
    let fixture: ComponentFixture<MarethaDocumentChatComponent>;
    let nuxeoSpy: jasmine.SpyObj<NuxeoService>;

    const mockInteraction = {
        id: 'chat-1',
        name: 'Chat with document',
        description: 'Ask anything about this document',
        parameters: [{ name: 'Question', type: 'string', value: '', readOnly: false }]
    };

    beforeEach(async () => {
        nuxeoSpy = jasmine.createSpyObj('NuxeoService', ['executeOperation', 'getClient']);
        nuxeoSpy.executeOperation.and.returnValue(Promise.resolve({ value: [mockInteraction] }));

        await TestBed.configureTestingModule({
            imports: [MarethaDocumentChatComponent],
            providers: [{ provide: NuxeoService, useValue: nuxeoSpy }]
        }).compileComponents();

        fixture = TestBed.createComponent(MarethaDocumentChatComponent);
        component = fixture.componentInstance;
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with empty chat context', () => {
        expect(component.chatContext).toEqual([]);
        expect(component.loading).toBeFalse();
    });

    it('should NOT call loadChatInteraction when no documentId is set', () => {
        component.ngOnInit();
        expect(nuxeoSpy.executeOperation).not.toHaveBeenCalled();
    });

    it('should call GetInteractionsForDocumentOperation when documentId is set', async () => {
        component.documentId = 'some-doc-uid';
        component.ngOnInit();
        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith(
            'GetInteractionsForDocumentOperation',
            { isChat: true },
            'some-doc-uid'
        );
    });

    it('should set chatInteraction after loading', async () => {
        component.documentId = 'some-doc-uid';
        component.ngOnInit();
        await fixture.whenStable();
        expect(component.chatInteraction).toEqual(mockInteraction);
    });

    it('should add messages and call RunInteractionOperation on interactWithDocument()', async () => {
        nuxeoSpy.executeOperation.and.callFake((op: string) => {
            if (op === 'GetInteractionsForDocumentOperation') return Promise.resolve({ value: [mockInteraction] });
            if (op === 'RunInteractionOperation') return Promise.resolve({ value: 'AI response here' });
            return Promise.resolve({});
        });

        component.documentId = 'some-doc-uid';
        component.ngOnInit();
        await fixture.whenStable();

        // Simulate user typing a question
        component.chatInteraction!.parameters[0].value = 'What is this document about?';
        component.interactWithDocument();

        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith(
            'RunInteractionOperation',
            jasmine.objectContaining({ interactionId: 'chat-1' }),
            'some-doc-uid'
        );
        expect(component.loading).toBeTrue(); // still true before promise resolves
    });

    it('should handle errors gracefully in interactWithDocument()', async () => {
        nuxeoSpy.executeOperation.and.callFake((op: string) => {
            if (op === 'GetInteractionsForDocumentOperation') return Promise.resolve({ value: [mockInteraction] });
            return Promise.reject(new Error('Network error'));
        });
        component.documentId = 'uid1';
        component.ngOnInit();
        await fixture.whenStable();

        component.chatInteraction!.parameters[0].value = 'question';
        component.interactWithDocument();
        // Flush macro-task queue so the rejected promise's catch handler runs
        await new Promise<void>(resolve => setTimeout(resolve, 10));
        await fixture.whenStable();

        const lastMsg = component.chatContext[component.chatContext.length - 1];
        expect(lastMsg.content).toContain('Error');
        expect(component.loading).toBeFalse();
    });

    it('should respond to document @Input changes via ngOnChanges', async () => {
        const changes: any = { document: { currentValue: { uid: 'new-uid' } } };
        component.document = { uid: 'new-uid' };
        component.ngOnChanges(changes);
        expect(nuxeoSpy.executeOperation).toHaveBeenCalledWith(
            'GetInteractionsForDocumentOperation', { isChat: true }, 'new-uid'
        );
    });
});
