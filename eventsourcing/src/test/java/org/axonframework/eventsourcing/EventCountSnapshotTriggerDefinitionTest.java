/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.utils.StubAggregate;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventCountSnapshotTriggerDefinition}.
 *
 * @author Allard Buijze
 */
class EventCountSnapshotTriggerDefinitionTest {

    private EventCountSnapshotTriggerDefinition testSubject;
    private Snapshotter mockSnapshotter;
    private String aggregateIdentifier;
    private Aggregate<?> aggregate;

    @BeforeEach
    void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        mockSnapshotter = mock(Snapshotter.class);
        testSubject = new EventCountSnapshotTriggerDefinition(mockSnapshotter, 3);
        aggregateIdentifier = "aggregateIdentifier";
        DefaultUnitOfWork.startAndGet(new GenericMessage<>("test"));
        aggregate = AnnotatedAggregate.initialize(
                new StubAggregate(aggregateIdentifier),
                AnnotatedAggregateMetaModelFactory.inspectAggregate(StubAggregate.class),
                null
        );
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void snapshotterTriggeredOnUnitOfWorkCleanup() {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.get()
                         .onCommit(uow -> verify(mockSnapshotter, never())
                                 .scheduleSnapshot(aggregate.rootType(), aggregateIdentifier));
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    void snapshotterTriggeredOnUnitOfWorkCommit() {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.initializationFinished();
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    void snapshotterIsNotTriggeredOnUnitOfWorkRollbackIfEventsHandledAfterInitialization() {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.initializationFinished();
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.get().rollback();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    void snapshotterTriggeredOnUnitOfWorkRollbackWhenEventsHandledBeforeInitialization() {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.initializationFinished();

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.get().rollback();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }

    @Test
    void snapshotterNotTriggered() {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.getClass(), aggregateIdentifier);
    }

    @Test
    void counterDoesNotResetWhenSerialized() throws IOException, ClassNotFoundException {
        SnapshotTrigger trigger = testSubject.prepareTrigger(aggregate.rootType());
        GenericDomainEventMessage<String> msg = new GenericDomainEventMessage<>(
                "type", aggregateIdentifier, 0, "Mock contents", MetaData.emptyInstance()
        );
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);
        trigger.eventHandled(msg);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(trigger);
        trigger = (SnapshotTrigger) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();
        testSubject.reconfigure(aggregate.rootType(), trigger);
        // this triggers the snapshot
        trigger.eventHandled(msg);

        verify(mockSnapshotter, never()).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
        CurrentUnitOfWork.commit();
        verify(mockSnapshotter).scheduleSnapshot(aggregate.rootType(), aggregateIdentifier);
    }
}
