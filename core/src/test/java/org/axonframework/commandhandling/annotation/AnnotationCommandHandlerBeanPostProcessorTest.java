/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.junit.*;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationCommandHandlerBeanPostProcessorTest {

    private AnnotationCommandHandlerBeanPostProcessor testSubject;
    private CommandBus mockCommandBus;
    private ApplicationContext mockApplicationContext;

    @Before
    public void setUp() {
        mockCommandBus = mock(CommandBus.class);
        mockApplicationContext = mock(ApplicationContext.class);
        testSubject = new AnnotationCommandHandlerBeanPostProcessor();
        testSubject.setCommandBus(mockCommandBus);
        testSubject.setApplicationContext(mockApplicationContext);
    }

    @Test
    public void testCommandBusIsNotAutowiredWhenProvided() throws Exception {

        testSubject.initializeAdapterFor(new Object(), mock(ParameterResolverFactory.class));

        verify(mockApplicationContext, never()).getBeansOfType(CommandBus.class);
    }

    @Test
    public void testCommandBusIsAutowired() throws Exception {
        testSubject.setCommandBus(null);
        Map<String, CommandBus> map = new HashMap<String, CommandBus>();
        map.put("ignored", mockCommandBus);
        when(mockApplicationContext.getBeansOfType(CommandBus.class)).thenReturn(map);

        AnnotationCommandHandlerAdapter adapter = mock(AnnotationCommandHandlerAdapter.class);
        when(adapter.supportedCommands()).thenReturn(Collections.singleton("test"));
        CommandHandler handler = mock(CommandHandler.class);

        testSubject.subscribe(handler, adapter);

        verify(mockApplicationContext).getBeansOfType(CommandBus.class);
        verify(mockCommandBus).subscribe(anyString(), eq(handler));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testEventHandlerCallsRedirectToAdapter() throws Throwable {
        Object result1 = testSubject.postProcessBeforeInitialization(new AnnotatedCommandHandler(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof CommandHandler);
        assertTrue(postProcessedBean instanceof AnnotatedCommandHandler);

        CommandHandler<MyCommand> commandHandler = (CommandHandler<MyCommand>) postProcessedBean;
        AnnotatedCommandHandler annotatedCommandHandler = (AnnotatedCommandHandler) postProcessedBean;
        CommandMessage<MyCommand> myCommand = GenericCommandMessage.asCommandMessage(new MyCommand());
        commandHandler.handle(myCommand, null);

        assertEquals(1, annotatedCommandHandler.getInvocationCount());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testEventHandlerAdapterIsInitializedAndDestroyedProperly_NoStopSignal() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new AnnotatedCommandHandler(), "beanName");
        CommandHandler postProcessedBean = (CommandHandler) testSubject.postProcessAfterInitialization(result1,
                                                                                                       "beanName");

        verify(mockCommandBus, never()).subscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));
        verify(mockCommandBus, never()).unsubscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));

        testSubject.start();

        verify(mockCommandBus).subscribe(eq(MyCommand.class.getName()), eq(postProcessedBean));
        verify(mockCommandBus, never()).unsubscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));

        // it should unregister even when the postprocessor isn't explicitly stopped
        testSubject.postProcessBeforeDestruction(postProcessedBean, "beanName");
        verify(mockCommandBus).unsubscribe(eq(MyCommand.class.getName()), eq(postProcessedBean));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testEventHandlerAdapterIsInitializedAndDestroyedProperly_NormalLifecycle() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new AnnotatedCommandHandler(), "beanName");
        CommandHandler postProcessedBean = (CommandHandler) testSubject.postProcessAfterInitialization(result1,
                                                                                                       "beanName");

        verify(mockCommandBus, never()).subscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));
        verify(mockCommandBus, never()).unsubscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));

        testSubject.start();

        verify(mockCommandBus).subscribe(eq(MyCommand.class.getName()), eq(postProcessedBean));
        verify(mockCommandBus, never()).unsubscribe(eq(MyCommand.class.getName()), isA(CommandHandler.class));

        testSubject.stop();
        verify(mockCommandBus).unsubscribe(eq(MyCommand.class.getName()), eq(postProcessedBean));
    }

    public static class AnnotatedCommandHandler {

        private int invocationCount;

        @SuppressWarnings({"UnusedDeclaration"})
        @org.axonframework.commandhandling.annotation.CommandHandler
        public void handleCommand(MyCommand command) {
            invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    private static class MyCommand {

    }
}