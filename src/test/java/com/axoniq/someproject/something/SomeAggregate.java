/*
 * Copyright (c) 2010-2025. Axon Framework
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

package com.axoniq.someproject.something;

import com.axoniq.someproject.api.AddChildToListCommand;
import com.axoniq.someproject.api.AddChildToMapCommand;
import com.axoniq.someproject.api.ChangeStatusCommand;
import com.axoniq.someproject.api.ChildAddedToListEvent;
import com.axoniq.someproject.api.ChildAddedToMapEvent;
import com.axoniq.someproject.api.SomeCommand;
import com.axoniq.someproject.api.SomeEvent;
import com.axoniq.someproject.api.StatusChangedEvent;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.interception.annotation.ExceptionHandler;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.annotation.EntityMember;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@EventSourcedEntity
public class SomeAggregate {

    @EntityMember
    private final List<SomeAggregateChild> childList = new ArrayList<>();
    @EntityMember
    private final Map<String, SomeAggregateChild> childMap = new HashMap<>();
    private String id;
    private String status;
    @EntityMember
    private SingleAggregateChild child;

    public SomeAggregate(SomeCommand command) {
        // Entity creation
    }

    public SomeAggregate() {
        // Required by Axon to construct an empty instance to initiate Event Sourcing.
    }

    @ExceptionHandler
    public void exceptionHandler(Exception error) throws Exception {
        throw error;
    }

    @SuppressWarnings("rawtypes")
    @MessageHandlerInterceptor
    public Object intercept(Message message, MessageHandlerInterceptorChain chain,
                            ProcessingContext processingContext) throws Exception {
        return chain.proceed(message, processingContext);
    }

    @CommandHandler
    public void handle(ChangeStatusCommand command) {
        if (Objects.equals(status, command.newStatus())) {
            throw new IllegalStateException("new state should be different than current state");
        }
    }

    @CommandHandler
    public void handleAddToList(AddChildToListCommand command) {
        // left empty to not overcomplicate things
    }

    @CommandHandler
    public void handleAddToMap(AddChildToMapCommand command) {
        // left empty to not overcomplicate things
    }

    @EventSourcingHandler
    protected void onSomeEvent(SomeEvent event) {
        this.id = event.id();
    }

    @EventSourcingHandler
    protected void onStatusChangedEvent(StatusChangedEvent event) {
        this.status = event.newStatus();
    }

    @EventSourcingHandler
    protected void onAddedToList(ChildAddedToListEvent event) {
        this.childList.add(new SomeAggregateChild(event.id(), event.property()));
    }

    @EventSourcingHandler
    protected void onAddedToMap(ChildAddedToMapEvent event) {
        this.childMap.put(event.key(), new SomeAggregateChild(event.id(), event.property()));
    }
}
